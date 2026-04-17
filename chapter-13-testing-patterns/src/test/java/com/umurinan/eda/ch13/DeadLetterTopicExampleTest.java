package com.umurinan.eda.ch13;

import com.umurinan.eda.ch13.events.OrderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Pattern: Dead Letter Topic (DLT) testing
 *
 * When a listener throws an unchecked exception, Spring Kafka's
 * DefaultErrorHandler retries the message according to the configured BackOff.
 * After the retries are exhausted the DeadLetterPublishingRecoverer writes the
 * original message — headers and payload intact — to a topic named
 * "{original-topic}.DLT".
 *
 * Testing strategy:
 *   1. Publish a message that will always fail (orderId == null triggers
 *      IllegalArgumentException in OrderEventHandler).
 *   2. Subscribe to orders.DLT with a @KafkaListener defined in a nested
 *      @TestConfiguration class — this is essential. The test class itself
 *      is not a Spring bean, so a @KafkaListener declared directly on it
 *      is never registered. A @TestConfiguration inner class IS a bean.
 *   3. Use Awaitility to wait until the DLT listener captures the message.
 *   4. Assert the captured payload matches what we sent.
 *
 * This test proves the full failure path end-to-end: publish → listener fails
 * → retries exhausted → DLT receives message.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"orders", "orders.DLT"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("DLT: Failed messages are routed to the dead letter topic")
class DeadLetterTopicExampleTest {

    /**
     * A proper Spring bean that holds the captured DLT message.
     *
     * The @KafkaListener annotation is only processed when placed on a method
     * of a Spring-managed bean. Putting it here — inside a @TestConfiguration
     * class — guarantees registration. The AtomicReference is then injected
     * into the test via @Autowired so the test method can assert against it.
     */
    @TestConfiguration
    static class DltListenerConfig {

        final AtomicReference<OrderEvent> captured = new AtomicReference<>();

        @Bean
        DltListenerConfig.DltCapture dltCapture() {
            return new DltCapture(captured);
        }

        static class DltCapture {
            private final AtomicReference<OrderEvent> captured;

            DltCapture(AtomicReference<OrderEvent> captured) {
                this.captured = captured;
            }

            @KafkaListener(topics = "orders.DLT", groupId = "dlt-test-consumer")
            void onDlt(OrderEvent event) {
                captured.set(event);
            }
        }
    }

    @Autowired
    private DltListenerConfig dltListenerConfig;

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Test
    @DisplayName("message with null orderId is routed to orders.DLT after retries")
    void nullOrderIdLandsOnDlt() {
        // A null orderId causes OrderEventHandler to throw IllegalArgumentException.
        // DefaultErrorHandler will retry twice (FixedBackOff 500 ms, 2 attempts)
        // before handing the message to DeadLetterPublishingRecoverer.
        var poisonEvent = new OrderEvent(null, "customer-dlt", new BigDecimal("9.99"));

        kafkaTemplate.send("orders", poisonEvent);

        // Allow up to 20 seconds: 2 retries × 500 ms + listener startup + margin.
        await()
                .atMost(20, SECONDS)
                .untilAsserted(() ->
                        assertThat(dltListenerConfig.captured.get()).isNotNull()
                );

        assertThat(dltListenerConfig.captured.get().customerId()).isEqualTo("customer-dlt");
        assertThat(dltListenerConfig.captured.get().orderId()).isNull();
    }
}
