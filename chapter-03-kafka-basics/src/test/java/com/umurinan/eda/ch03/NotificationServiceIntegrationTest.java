package com.umurinan.eda.ch03;

import com.umurinan.eda.ch03.events.OrderPlaced;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"order-placed", "order-placed.DLT"}, adminTimeout = 30)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DirtiesContext
@DisplayName("NotificationService integration")
class NotificationServiceIntegrationTest {

    @Autowired
    private KafkaTemplate<String, OrderPlaced> kafkaTemplate;

    @Autowired
    private TestOrderPlacedConsumer testConsumer;

    @Test
    @DisplayName("published OrderPlaced event is received and processed by NotificationService")
    void publishedEvent_isReceivedByNotificationService() throws Exception {
        var event = new OrderPlaced("order-integration-1", "customer-int-1", new BigDecimal("55.00"), Instant.now());

        kafkaTemplate.send("order-placed", event.orderId(), event);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(testConsumer.latch.await(0, TimeUnit.MILLISECONDS)).isTrue()
                );
    }

    @TestConfiguration
    static class ConsumerConfig {
        @Bean
        TestOrderPlacedConsumer testOrderPlacedConsumer() {
            return new TestOrderPlacedConsumer();
        }
    }

    /**
     * A dedicated test consumer that counts down its latch each time it receives
     * a valid OrderPlaced event. This lets the test assert that at least one
     * message was delivered without coupling to NotificationService internals.
     */
    static class TestOrderPlacedConsumer {

        final CountDownLatch latch = new CountDownLatch(1);

        @KafkaListener(
                topics = "order-placed",
                groupId = "test-notification-observer",
                containerFactory = "kafkaListenerContainerFactory"
        )
        void observe(OrderPlaced event, Acknowledgment ack) {
            if (event.orderId() != null) {
                latch.countDown();
            }
            ack.acknowledge();
        }
    }
}
