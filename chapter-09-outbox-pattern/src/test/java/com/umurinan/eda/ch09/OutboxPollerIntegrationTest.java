package com.umurinan.eda.ch09;

import com.umurinan.eda.ch09.outbox.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"order-placed"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("OutboxPoller integration — relay publishes outbox messages to Kafka")
class OutboxPollerIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private TestKafkaListener testKafkaListener;

    @Test
    @DisplayName("after placeOrder(), poller publishes the message and listener receives it with correct customerId")
    void placeOrder_pollerPublishesToKafka_listenerReceivesMessage() {
        orderService.placeOrder("customer-001");

        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc()).isEmpty()
                );

        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var received = testKafkaListener.getLastPayload();
                    assertThat(received).isNotNull();
                    assertThat(received).contains("customer-001");
                });
    }

    @TestConfiguration
    static class TestListenerConfig {

        @Bean
        TestKafkaListener testKafkaListener() {
            return new TestKafkaListener();
        }
    }

    static class TestKafkaListener {

        private final AtomicReference<String> lastPayload = new AtomicReference<>();

        @KafkaListener(topics = "order-placed", groupId = "outbox-integration-test")
        void onMessage(String payload) {
            lastPayload.set(payload);
        }

        String getLastPayload() {
            return lastPayload.get();
        }
    }
}
