package com.umurinan.eda.ch03;

import com.umurinan.eda.ch03.events.OrderPlaced;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"order-placed", "order-placed.DLT"}, adminTimeout = 30)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DirtiesContext
@DisplayName("Dead Letter Topic routing")
class DeadLetterTopicTest {

    @Autowired
    private KafkaTemplate<String, OrderPlaced> kafkaTemplate;

    @Autowired
    private DltCapture dltCapture;

    /**
     * Replace NotificationService with a bean that always throws so every
     * message exhausts its retries and lands on the DLT.
     */
    @TestConfiguration
    static class AlwaysFailingNotificationConfig {

        @Bean
        @Primary
        NotificationService alwaysFailingNotificationService() {
            return new NotificationService() {
                @Override
                @KafkaListener(topics = "order-placed", groupId = "notification-service")
                public void onOrderPlaced(OrderPlaced event, Acknowledgment ack) {
                    throw new RuntimeException("Simulated processing failure for DLT test");
                }
            };
        }

        @Bean
        DltCapture dltCapture() {
            return new DltCapture();
        }
    }

    @Test
    @DisplayName("message that always fails is routed to order-placed.DLT after retries are exhausted")
    void failingMessage_isRoutedToDlt() {
        var event = new OrderPlaced(null, "customer-dlt-1", new BigDecimal("10.00"), Instant.now());

        kafkaTemplate.send("order-placed", event.orderId(), event);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(dltCapture.latch.await(0, TimeUnit.MILLISECONDS)).isTrue()
                );

        assertThat(dltCapture.received.get()).isNotNull();
    }

    /**
     * Test listener that sits on the DLT and records the first message it sees.
     */
    static class DltCapture {

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<OrderPlaced> received = new AtomicReference<>();

        @KafkaListener(
                topics = "order-placed.DLT",
                groupId = "test-dlt-capture",
                containerFactory = "kafkaListenerContainerFactory"
        )
        void capture(OrderPlaced event, Acknowledgment ack) {
            received.set(event);
            latch.countDown();
            ack.acknowledge();
        }
    }
}
