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
 * Pattern: Basic EmbeddedKafka + Awaitility
 *
 * This is the foundation of async Kafka testing. The key insight is that
 * @EmbeddedKafka spins up a real (but in-process) Kafka broker, so the
 * listener wiring is identical to production. The only thing that changes
 * is the bootstrap-servers address, supplied via @TestPropertySource.
 *
 * WHY Awaitility instead of Thread.sleep?
 *
 * Thread.sleep(5000) always waits the full 5 seconds, even when the message
 * arrives in 50 ms. More importantly, it gives you no useful failure message
 * when the condition never becomes true. Awaitility polls until the assertion
 * passes or the deadline is hit, making tests both faster and more readable:
 *
 *   await().atMost(10, SECONDS).untilAsserted(() -> assertThat(...));
 *
 * If the condition is never satisfied, Awaitility reports the last assertion
 * error — which tells you exactly what the state was, not just "timed out".
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"orders", "orders.DLT"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("EmbeddedKafka: Basic async integration test pattern")
class EmbeddedKafkaExampleTest {

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Autowired
    private OrderEventHandler handler;

    @Test
    @DisplayName("published OrderEvent is processed by the listener within 10 seconds")
    void publishedEventIsProcessed() {
        var orderId = UUID.randomUUID().toString();
        var event = new OrderEvent(orderId, "customer-1", new BigDecimal("49.99"));

        kafkaTemplate.send("orders", orderId, event);

        // Awaitility keeps retrying the assertion until it passes or the
        // 10-second deadline is hit. No polling interval specified means it
        // defaults to 100 ms — plenty of resolution for a local broker.
        await()
                .atMost(10, SECONDS)
                .untilAsserted(() ->
                        assertThat(handler.processedOrders).containsKey(orderId)
                );
    }
}
