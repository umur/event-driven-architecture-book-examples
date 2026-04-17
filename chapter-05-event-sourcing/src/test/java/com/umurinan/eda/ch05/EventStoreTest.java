package com.umurinan.eda.ch05;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.umurinan.eda.ch05.domain.events.OrderCancelledEvent;
import com.umurinan.eda.ch05.domain.events.OrderPlacedEvent;
import com.umurinan.eda.ch05.store.EventStore;
import com.umurinan.eda.ch05.store.JdbcEventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.umurinan.eda.ch05.store.OptimisticConcurrencyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = EventStoreTest.TestConfig.class)
@Sql("/schema.sql")
class EventStoreTest {

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
    }

    @Autowired
    private EventStore eventStore;

    @Test
    void appendFirstEvent_storesSequenceNumberZero() {
        var aggregateId = "store-test-" + System.nanoTime();
        var event = new OrderPlacedEvent(aggregateId, "cust-1", new BigDecimal("10.00"), Instant.now());

        eventStore.append(aggregateId, event, 0L);

        var events = eventStore.loadEvents(aggregateId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(OrderPlacedEvent.class);
    }

    @Test
    void appendSecondEvent_storesSequenceNumberOne() {
        var aggregateId = "store-test-seq-" + System.nanoTime();
        var placed = new OrderPlacedEvent(aggregateId, "cust-2", new BigDecimal("20.00"), Instant.now());
        var cancelled = new OrderCancelledEvent(aggregateId, "test reason", Instant.now());

        eventStore.append(aggregateId, placed, 0L);
        eventStore.append(aggregateId, cancelled, 1L);

        var events = eventStore.loadEvents(aggregateId);
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(OrderPlacedEvent.class);
        assertThat(events.get(1)).isInstanceOf(OrderCancelledEvent.class);
    }

    @Test
    void loadEvents_returnsEventsInSequenceOrder() {
        var aggregateId = "store-test-order-" + System.nanoTime();
        var placed = new OrderPlacedEvent(aggregateId, "cust-3", new BigDecimal("30.00"), Instant.now());
        var cancelled = new OrderCancelledEvent(aggregateId, "ordering test", Instant.now());

        eventStore.append(aggregateId, placed, 0L);
        eventStore.append(aggregateId, cancelled, 1L);

        var events = eventStore.loadEvents(aggregateId);
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(OrderPlacedEvent.class);
        assertThat(events.get(1)).isInstanceOf(OrderCancelledEvent.class);
    }

    @Test
    void appendDuplicateSequenceNumber_throwsException() {
        var aggregateId = "store-test-dup-" + System.nanoTime();
        var first = new OrderPlacedEvent(aggregateId, "cust-4", new BigDecimal("40.00"), Instant.now());
        var duplicate = new OrderCancelledEvent(aggregateId, "conflict", Instant.now());

        eventStore.append(aggregateId, first, 0L);

        assertThatThrownBy(() -> eventStore.append(aggregateId, duplicate, 0L))
                .isInstanceOf(OptimisticConcurrencyException.class)
                .hasMessageContaining(aggregateId)
                .hasMessageContaining("0");
    }
}
