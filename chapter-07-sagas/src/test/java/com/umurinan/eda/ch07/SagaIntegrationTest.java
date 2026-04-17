package com.umurinan.eda.ch07;

import com.umurinan.eda.ch07.domain.OrderSagaRepository;
import com.umurinan.eda.ch07.domain.SagaState;
import com.umurinan.eda.ch07.replies.PaymentReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "payment-commands",
                "payment-replies",
                "inventory-commands",
                "inventory-replies",
                "shipment-commands",
                "shipment-replies"
        }
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "scheduling.enabled=false"
})
@DirtiesContext
@DisplayName("OrderSagaOrchestrator integration")
class SagaIntegrationTest {

    @Autowired
    private OrderSagaOrchestrator orchestrator;

    @Autowired
    private OrderSagaRepository repository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("startSaga() persists the saga with state PAYMENT_PROCESSING")
    void startSaga_persistsSagaWithCorrectState() {
        var orderId = "it-order-" + System.nanoTime();

        orchestrator.startSaga(orderId, new BigDecimal("100.00"));

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var found = repository.findById(orderId);
                    assertThat(found).isPresent();
                    assertThat(found.get().getState())
                            .isEqualTo(SagaState.PAYMENT_PROCESSING.name());
                });
    }

    @Test
    @DisplayName("PaymentReply(success) transitions saga to INVENTORY_RESERVING")
    void paymentSuccessReply_transitionsSagaToInventoryReserving() {
        var orderId = "it-order-pay-" + System.nanoTime();

        orchestrator.startSaga(orderId, new BigDecimal("100.00"));

        // Wait until the saga is persisted before sending the reply.
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> repository.findById(orderId).isPresent());

        var reply = new PaymentReply(orderId, true, "tx-it-123", null);
        kafkaTemplate.send("payment-replies", orderId, reply);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var saga = repository.findById(orderId);
                    assertThat(saga).isPresent();
                    assertThat(saga.get().getState())
                            .isEqualTo(SagaState.INVENTORY_RESERVING.name());
                });
    }
}
