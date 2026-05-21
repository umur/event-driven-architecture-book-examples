package com.umurinan.eda.ch07;

import com.umurinan.eda.ch07.commands.RefundPaymentCommand;
import com.umurinan.eda.ch07.domain.OrderSaga;
import com.umurinan.eda.ch07.domain.OrderSagaRepository;
import com.umurinan.eda.ch07.domain.SagaState;
import com.umurinan.eda.ch07.replies.InventoryReply;
import com.umurinan.eda.ch07.replies.PaymentReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for Saga compensation flows.
 *
 * When a Saga step fails, previously completed steps must be compensated.
 * This test suite verifies that compensation commands are correctly issued
 * and that the Saga transitions through the COMPENSATING state properly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Saga Compensation Tests")
class SagaCompensationTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private OrderSagaRepository repository;

    private OrderSagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new OrderSagaOrchestrator(kafkaTemplate, repository);
    }

    private void stubKafkaSend() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
            .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("compensation: failed inventory triggers refund")
    void compensation_failedInventory_refundsPayment() {
        // Arrange: Saga in PAYMENT_PROCESSING state (payment in progress)
        var saga = new OrderSaga("order-1");
        var ack = mock(Acknowledgment.class);
        
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        stubKafkaSend();
        
        // Simulate successful payment reply
        var paymentReply = new PaymentReply("order-1", true, "tx-abc", null);
        orchestrator.handlePaymentReply(paymentReply, ack);
        
        // Verify saga moved to INVENTORY_RESERVING
        assertThat(saga.getState()).isEqualTo(SagaState.INVENTORY_RESERVING.name());
        
        // Simulate inventory failure
        var inventoryReply = new InventoryReply("order-1", false, null, "out of stock");
        orchestrator.handleInventoryReply(inventoryReply, ack);
        
        // Assert: Refund command sent
        verify(kafkaTemplate).send(
            eq("payment-commands"),
            eq("order-1"),
            any(RefundPaymentCommand.class)
        );
        
        // Verify saga transitions to COMPENSATING
        assertThat(saga.getState()).isEqualTo(SagaState.COMPENSATING.name());
    }

    @Test
    @DisplayName("compensation: partial completion compensates only completed steps")
    void compensation_partialCompletion_compensatesOnlyCompleted() {
        // Arrange: Saga with payment in progress
        var saga = new OrderSaga("order-1");
        var ack = mock(Acknowledgment.class);
        
        when(repository.findById("order-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        stubKafkaSend();
        
        // Process payment
        var paymentReply = new PaymentReply("order-1", true, "tx-abc", null);
        orchestrator.handlePaymentReply(paymentReply, ack);
        
        // Act: Inventory fails before reservation
        var inventoryReply = new InventoryReply("order-1", false, null, "timeout");
        orchestrator.handleInventoryReply(inventoryReply, ack);
        
        // Assert: Refund sent for completed payment
        ArgumentCaptor<RefundPaymentCommand> refundCaptor = 
            ArgumentCaptor.forClass(RefundPaymentCommand.class);
        verify(kafkaTemplate).send(eq("payment-commands"), eq("order-1"), refundCaptor.capture());
        
        // Verify refund contains correct transaction ID
        assertThat(refundCaptor.getValue().transactionId()).isEqualTo("tx-abc");
    }

    @Test
    @DisplayName("compensation: idempotency key present on refund command")
    void compensation_refundCommand_hasIdempotencyKey() {
        // Arrange
        var saga = new OrderSaga("order-idempotent-1");
        var ack = mock(Acknowledgment.class);
        
        when(repository.findById("order-idempotent-1")).thenReturn(Optional.of(saga));
        when(repository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));
        stubKafkaSend();
        
        // Complete payment
        var paymentReply = new PaymentReply("order-idempotent-1", true, "tx-idempotent", null);
        orchestrator.handlePaymentReply(paymentReply, ack);
        
        // Fail inventory
        var inventoryReply = new InventoryReply("order-idempotent-1", false, null, "fail");
        orchestrator.handleInventoryReply(inventoryReply, ack);
        
        // Assert: Refund command has non-null idempotency key
        ArgumentCaptor<RefundPaymentCommand> captor = 
            ArgumentCaptor.forClass(RefundPaymentCommand.class);
        verify(kafkaTemplate).send(eq("payment-commands"), eq("order-idempotent-1"), captor.capture());
        
        assertThat(captor.getValue().idempotencyKey()).isNotNull();
    }
}
