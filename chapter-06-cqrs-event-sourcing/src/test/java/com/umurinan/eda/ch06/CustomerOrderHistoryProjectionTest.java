package com.umurinan.eda.ch06;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.umurinan.eda.ch06.events.OrderPlacedEvent;
import com.umurinan.eda.ch06.readmodel.CustomerOrderHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(topics = {"order-events"}, partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
class CustomerOrderHistoryProjectionTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private CustomerOrderHistoryRepository customerOrderHistoryRepository;

    private final ObjectMapper objectMapper = buildObjectMapper();

    @BeforeEach
    void waitForConsumersReady() throws Exception {
        for (var container : kafkaListenerEndpointRegistry.getAllListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }
        Thread.sleep(500);
    }

    @Test
    void publishOrderPlacedEvent_customerOrderHistoryProjectionSavesEntry() throws Exception {
        var orderId = "hist-" + UUID.randomUUID();
        var customerId = "cust-hist-" + UUID.randomUUID();
        var total = new BigDecimal("75.50");

        var event = new OrderPlacedEvent(orderId, customerId, total, Instant.now());
        var payload = buildPayload("ORDER_PLACED", event);

        kafkaTemplate.send("order-events", orderId, payload);

        await().atMost(10, SECONDS).untilAsserted(() -> {
            var history = customerOrderHistoryRepository.findByCustomerId(customerId);
            assertThat(history).isNotEmpty();
            assertThat(history.get(0).getOrderId()).isEqualTo(orderId);
            assertThat(history.get(0).getTotal()).isEqualByComparingTo(total);
        });
    }

    private String buildPayload(String eventType, Object event) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("eventType", eventType);
        node.set("payload", objectMapper.valueToTree(event));
        return objectMapper.writeValueAsString(node);
    }

    private static ObjectMapper buildObjectMapper() {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
