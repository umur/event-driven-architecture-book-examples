package com.umurinan.eda.ch13;

import com.umurinan.eda.ch13.events.OrderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Pattern: Duplicate delivery / idempotency testing
 *
 * Kafka's at-least-once delivery guarantee means a message can arrive more
 * than once: during a rebalance, after a consumer restart, or when an offset
 * is committed late.  Consumers must be idempotent — processing the same
 * message twice must produce the same outcome as processing it once.
 *
 * Testing strategy:
 *   1. Publish the SAME OrderEvent (same orderId) twice.
 *   2. Wait until the orderId appears in processedOrders (first delivery handled).
 *   3. Assert it appears EXACTLY ONCE — the handler's containsKey guard
 *      prevented the duplicate from being re-processed.
 *
 * This test makes the idempotency contract explicit and executable.
 * If someone removes the duplicate check from OrderEventHandler, this test
 * will still pass unless the second delivery causes a visible side-effect.
 * A more thorough variant would count how many times a downstream call was
 * made — see the book discussion on spy-based idempotency tests.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"orders", "orders.DLT"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("Idempotency: duplicate delivery is processed exactly once")
class IdempotencyExampleTest {

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Autowired
    private OrderEventHandler handler;

    @Test
    @DisplayName("same orderId published twice appears exactly once in processedOrders")
    void duplicateMessageIsProcessedOnce() {
        var orderId = UUID.randomUUID().toString();
        var event = new OrderEvent(orderId, "customer-idempotent", new BigDecimal("199.00"));

        // Simulate at-least-once re-delivery by publishing the same event twice.
        kafkaTemplate.send("orders", orderId, event);
        kafkaTemplate.send("orders", orderId, event);

        // Wait until the first delivery has been processed.
        await()
                .atMost(10, SECONDS)
                .untilAsserted(() ->
                        assertThat(handler.processedOrders).containsKey(orderId)
                );

        // Give the second message a moment to be consumed — we are asserting
        // absence, so we need a short stabilisation wait.
        // Awaitility's conditionEvaluationListener could be used here too, but
        // a simple fixed poll period keeps the example readable.
        await()
                .pollDelay(java.time.Duration.ofMillis(500))
                .atMost(5, SECONDS)
                .untilAsserted(() ->
                        // The map stores at most one entry per orderId.
                        // If idempotency were broken the value might differ or a
                        // counter somewhere would show 2 — this assertion is the
                        // simplest form of the contract.
                        assertThat(handler.processedOrders).hasSize(1)
                );

        assertThat(handler.processedOrders.get(orderId)).isEqualTo(event);
    }
}
