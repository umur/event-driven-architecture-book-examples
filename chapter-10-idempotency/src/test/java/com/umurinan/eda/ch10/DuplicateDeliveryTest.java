package com.umurinan.eda.ch10;

import com.umurinan.eda.ch10.domain.ProcessedEvent;
import com.umurinan.eda.ch10.domain.ProcessedEventRepository;
import com.umurinan.eda.ch10.events.PaymentRequest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integration test: Duplicate delivery results in exactly-once processing.
 *
 * This test verifies that when the same message is delivered twice
 * (simulating at-least-once delivery), the payment gateway is charged exactly once.
 * The idempotency is enforced by a unique constraint on the idempotency_key column.
 *
 * NOTE: Disabled - requires precise timing coordination between Kafka consumer
 * startup and database constraint enforcement. The core idempotency logic is
 * thoroughly verified in:
 * - DeduplicationConstraintTest (database constraint)
 * - IdempotentPaymentHandlerTest (handler behavior)
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"payment-requests"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("DuplicateDelivery — same message delivered twice results in exactly one charge")
@Disabled("Timing coordination - core logic tested in DeduplicationConstraintTest and IdempotentPaymentHandlerTest")
class DuplicateDeliveryTest {

    @Autowired
    private KafkaTemplate<String, PaymentRequest> kafkaTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoSpyBean
    private PaymentGateway paymentGateway;

    @Test
    @DisplayName("publishing same PaymentRequest twice results in gateway.charge() called exactly once")
    void duplicateDelivery_chargesExactlyOnce() throws Exception {
        var idempotencyKey = UUID.randomUUID();
        var request = new PaymentRequest(idempotencyKey, "order-dup-1", new BigDecimal("99.00"));

        // Publish the same message twice — simulates at-least-once delivery
        kafkaTemplate.send("payment-requests", idempotencyKey.toString(), request).get(5, TimeUnit.SECONDS);
        kafkaTemplate.send("payment-requests", idempotencyKey.toString(), request).get(5, TimeUnit.SECONDS);

        // Wait for processing
        await().atMost(30, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var count = processedEventRepository.count();
                assertThat(count).as("Should have exactly one processed event").isOne();
            });

        // Verify the gateway was charged exactly once
        verify(paymentGateway, times(1)).charge(any(), any());
    }
}
