package com.umurinan.eda.ch10;

import com.umurinan.eda.ch10.domain.PaymentResult;
import com.umurinan.eda.ch10.domain.ProcessedEvent;
import com.umurinan.eda.ch10.domain.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for idempotent payment handling.
 *
 * Key insight: Idempotency is enforced at the database level via a unique
 * constraint on the idempotency_key column. The first call saves a ProcessedEvent
 * record; duplicates throw DataIntegrityViolationException which we catch and
 * return ALREADY_PROCESSED.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotentPaymentHandler")
class IdempotentPaymentHandlerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private PaymentGateway paymentGateway;

    private IdempotentPaymentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IdempotentPaymentHandler(processedEventRepository, paymentGateway);
    }

    @Test
    @DisplayName("first call: saves ProcessedEvent and charges via gateway")
    void firstCall_savesAndCharges() {
        var key = UUID.randomUUID();
        var orderId = "order-1";
        var amount = new BigDecimal("50.00");
        var expectedTxId = UUID.randomUUID();

        // First call: no existing record, so save succeeds
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
            .thenReturn(new ProcessedEvent(key));
        when(paymentGateway.charge(orderId, amount))
            .thenReturn(new PaymentResult(expectedTxId, "PROCESSED", Instant.now()));

        var result = handler.processPayment(key, orderId, amount);

        verify(processedEventRepository).saveAndFlush(any(ProcessedEvent.class));
        verify(paymentGateway).charge(orderId, amount);
        assertThat(result.status()).isEqualTo("PROCESSED");
        assertThat(result.transactionId()).isEqualTo(expectedTxId);
    }

    @Test
    @DisplayName("duplicate call: catches constraint violation and skips charge")
    void duplicateCall_skipsGatewayAndDoesNotSave() {
        var key = UUID.randomUUID();
        var orderId = "order-1";
        var amount = new BigDecimal("50.00");

        // Duplicate call: saveAndFlush throws constraint violation
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var result = handler.processPayment(key, orderId, amount);

        // Should skip payment gateway entirely
        verify(paymentGateway, never()).charge(any(), any());
        assertThat(result.status()).isEqualTo("ALREADY_PROCESSED");
    }

    @Test
    @DisplayName("processPayment() returns a PaymentResult regardless of first or duplicate call")
    void alwaysReturnsPaymentResult() {
        var key = UUID.randomUUID();
        var orderId = "order-2";
        var amount = new BigDecimal("75.00");
        var expectedTxId = UUID.randomUUID();

        // First call succeeds
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
            .thenReturn(new ProcessedEvent(key));
        when(paymentGateway.charge(orderId, amount))
            .thenReturn(new PaymentResult(expectedTxId, "PROCESSED", Instant.now()));

        var firstResult = handler.processPayment(key, orderId, amount);
        assertThat(firstResult).isNotNull();
        assertThat(firstResult.status()).isEqualTo("PROCESSED");
        assertThat(firstResult.transactionId()).isEqualTo(expectedTxId);

        // Duplicate call returns ALREADY_PROCESSED
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var duplicateResult = handler.processPayment(key, orderId, amount);
        assertThat(duplicateResult).isNotNull();
        assertThat(duplicateResult.status()).isEqualTo("ALREADY_PROCESSED");
    }

    @Test
    @DisplayName("concurrent: first request wins, second returns ALREADY_PROCESSED")
    void concurrentRequests_firstWins() {
        var key = UUID.randomUUID();
        var orderId = "order-concurrent";
        var amount = new BigDecimal("100.00");
        var txId = UUID.randomUUID();

        // First call succeeds, second throws constraint violation
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenReturn(new ProcessedEvent(key))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(paymentGateway.charge(orderId, amount))
                .thenReturn(new PaymentResult(txId, "PROCESSED", Instant.now()));

        var result1 = handler.processPayment(key, orderId, amount);
        var result2 = handler.processPayment(key, orderId, amount);

        assertThat(result1.status()).isEqualTo("PROCESSED");
        assertThat(result2.status()).isEqualTo("ALREADY_PROCESSED");
        verify(paymentGateway).charge(orderId, amount);
    }

    @Test
    @DisplayName("concurrent: multiple threads with same key only charge once")
    void concurrentRequests_sameKey_singleCharge() throws Exception {
        var key = UUID.randomUUID();
        var orderId = "order-threaded";
        var amount = new BigDecimal("50.00");
        var txId = UUID.randomUUID();

        AtomicInteger callCount = new AtomicInteger(0);
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(inv -> {
                    if (callCount.incrementAndGet() == 1) {
                        return new ProcessedEvent(key);
                    }
                    throw new DataIntegrityViolationException("duplicate key");
                });
        when(paymentGateway.charge(orderId, amount))
                .thenReturn(new PaymentResult(txId, "PROCESSED", Instant.now()));

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                var result = handler.processPayment(key, orderId, amount);
                if (result.status().equals("PROCESSED")) {
                    processedCount.incrementAndGet();
                } else {
                    duplicateCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(processedCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(threadCount - 1);
    }
}
