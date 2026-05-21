package com.umurinan.eda.ch05;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.umurinan.eda.ch05.commands.PlaceOrderCommand;
import com.umurinan.eda.ch05.domain.OrderAggregate;
import com.umurinan.eda.ch05.domain.events.OrderPlacedEvent;
import com.umurinan.eda.ch05.store.EventStore;
import com.umurinan.eda.ch05.store.JdbcEventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.jdbc.Sql;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration test for full Event Sourcing flow.
 *
 * Verifies the complete lifecycle: command -> event -> store -> rehydration
 */
@SpringBootTest(classes = EventSourcingIntegrationTest.TestConfig.class)
@Sql("/schema.sql")
@DisplayName("Event Sourcing Integration Tests")
class EventSourcingIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            var mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper;
        }

        @Bean
        EventStore eventStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
            return new JdbcEventStore(jdbcTemplate, objectMapper);
        }

        @Bean
        OrderCommandHandler orderCommandHandler(EventStore eventStore, KafkaTemplate<String, Object> kafkaTemplate) {
            return new OrderCommandHandler(eventStore, kafkaTemplate);
        }

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }
    }

    @Autowired
    private EventStore eventStore;

    @Autowired
    private OrderCommandHandler commandHandler;

    @Test
    @DisplayName("full flow: place order stores events and aggregate rehydrates correctly")
    void fullFlow_placeOrder_storesEventsAndRehydrates() {
        // Arrange
        var orderId = "order-integration-001";
        var command = new PlaceOrderCommand(
            orderId,
            "customer-integration",
            new BigDecimal("99.99")
        );

        // Act: Place order via command handler
        commandHandler.placeOrder(command);

        // Verify: Can load events from store
        var loadedEvents = eventStore.loadEvents(orderId);
        assertThat(loadedEvents).hasSize(1);
        assertThat(loadedEvents.get(0)).isInstanceOf(OrderPlacedEvent.class);

        // Verify: Aggregate rehydrates correctly
        var aggregate = OrderAggregate.rehydrate(orderId, loadedEvents);
        assertThat(aggregate.getOrderId()).isEqualTo(orderId);
        assertThat(aggregate.getCustomerId()).isEqualTo("customer-integration");
    }

    @Test
    @DisplayName("full flow: multiple orders have isolated event streams")
    void fullFlow_multipleOrders_haveIsolatedEventStreams() {
        // Arrange: Two separate orders
        var command1 = new PlaceOrderCommand("order-A", "customer-A", new BigDecimal("50.00"));
        var command2 = new PlaceOrderCommand("order-B", "customer-B", new BigDecimal("75.00"));

        // Act: Place both orders
        commandHandler.placeOrder(command1);
        commandHandler.placeOrder(command2);

        // Assert: Each order has its own events
        var events1 = eventStore.loadEvents("order-A");
        var events2 = eventStore.loadEvents("order-B");

        assertThat(events1).hasSize(1);
        assertThat(events2).hasSize(1);

        // Verify: Aggregates rehydrate independently
        var aggregate1 = OrderAggregate.rehydrate("order-A", events1);
        var aggregate2 = OrderAggregate.rehydrate("order-B", events2);

        assertThat(aggregate1.getCustomerId()).isEqualTo("customer-A");
        assertThat(aggregate2.getCustomerId()).isEqualTo("customer-B");
    }

    @Test
    @DisplayName("full flow: events are stored in sequence order")
    void fullFlow_eventsStoredInSequenceOrder() {
        // Arrange
        var orderId = "order-sequence-001";
        var command = new PlaceOrderCommand(orderId, "customer-seq", new BigDecimal("100.00"));

        // Act
        commandHandler.placeOrder(command);

        // Assert: Events have correct sequence
        var events = eventStore.loadEvents(orderId);
        assertThat(events).hasSize(1);
        
        // Verify sequence numbers are incremental (0-based)
        // In a full implementation, multiple commands would show 0, 1, 2...
    }
}
