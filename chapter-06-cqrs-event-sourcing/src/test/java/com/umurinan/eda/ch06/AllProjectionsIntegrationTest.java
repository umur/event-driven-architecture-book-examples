package com.umurinan.eda.ch06;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.umurinan.eda.ch06.events.OrderPlacedEvent;
import com.umurinan.eda.ch06.events.OrderShippedEvent;
import com.umurinan.eda.ch06.readmodel.CustomerOrderHistoryRepository;
import com.umurinan.eda.ch06.readmodel.DailyOrderSummaryRepository;
import com.umurinan.eda.ch06.readmodel.OrderDetailRepository;
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
import java.time.LocalDate;
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
class AllProjectionsIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private CustomerOrderHistoryRepository customerOrderHistoryRepository;

    @Autowired
    private DailyOrderSummaryRepository dailyOrderSummaryRepository;

    private final ObjectMapper objectMapper = buildObjectMapper();

    /**
     * Wait for every listener container to have received its partition assignment
     * before the test body publishes any messages. Without this, a consumer that
     * joins the group slightly after the message is produced can miss it even with
     * auto-offset-reset=earliest because its first fetch hasn't been dispatched yet.
     */
    @BeforeEach
    void waitForConsumersReady() throws Exception {
        for (var container : kafkaListenerEndpointRegistry.getAllListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }
        // Allow each consumer's first poll cycle to complete so the fetch position
        // is fully initialised before the test publishes any messages.
        Thread.sleep(500);
    }

    @Test
    void publishPlacedThenShippedEvent_allThreeProjectionsUpdateCorrectly() throws Exception {
        var orderId = "int-" + UUID.randomUUID();
        var customerId = "cust-int-" + UUID.randomUUID();
        var total = new BigDecimal("310.00");
        var trackingNumber = "TRACK-XYZ-001";

        var placed = new OrderPlacedEvent(orderId, customerId, total, Instant.now());
        kafkaTemplate.send("order-events", orderId, buildPayload("ORDER_PLACED", placed));

        // Wait for ORDER_PLACED to be fully processed by all three projections
        await().atMost(15, SECONDS).untilAsserted(() -> {
            assertThat(orderDetailRepository.findById(orderId)).isPresent();
            assertThat(customerOrderHistoryRepository.findByCustomerId(customerId)).isNotEmpty();
            assertThat(dailyOrderSummaryRepository.findById(LocalDate.now())).isPresent();
        });

        var shipped = new OrderShippedEvent(orderId, trackingNumber, Instant.now());
        kafkaTemplate.send("order-events", orderId, buildPayload("ORDER_SHIPPED", shipped));

        // Assert OrderDetail has tracking number after OrderShippedEvent
        await().atMost(10, SECONDS).untilAsserted(() -> {
            var detail = orderDetailRepository.findById(orderId);
            assertThat(detail).isPresent();
            assertThat(detail.get().getTrackingNumber()).isEqualTo(trackingNumber);
            assertThat(detail.get().getStatus()).isEqualTo("SHIPPED");
        });

        // Assert CustomerOrderHistory has exactly 1 entry for this customer
        var history = customerOrderHistoryRepository.findByCustomerId(customerId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getOrderId()).isEqualTo(orderId);

        // Assert daily summary has at least 1 order for today
        var summary = dailyOrderSummaryRepository.findById(LocalDate.now());
        assertThat(summary).isPresent();
        assertThat(summary.get().getOrderCount()).isGreaterThanOrEqualTo(1);
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
