package com.umurinan.eda.ch05.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umurinan.eda.ch05.domain.events.OrderCancelledEvent;
import com.umurinan.eda.ch05.domain.events.OrderEvent;
import com.umurinan.eda.ch05.domain.events.OrderPlacedEvent;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcEventStore implements EventStore {

    private static final String INSERT_SQL = """
            INSERT INTO order_events
                (aggregate_id, event_type, payload, sequence_number, occurred_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT event_type, payload
            FROM order_events
            WHERE aggregate_id = ?
            ORDER BY sequence_number ASC
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcEventStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(String aggregateId, OrderEvent event, long expectedSequence) {
        var eventType = resolveEventType(event);
        var payload = serialize(event);
        var occurredAt = resolveOccurredAt(event);

        try {
            jdbcTemplate.update(INSERT_SQL,
                    aggregateId,
                    eventType,
                    payload,
                    expectedSequence,
                    Timestamp.from(occurredAt));
        } catch (DataIntegrityViolationException ex) {
            throw new OptimisticConcurrencyException(aggregateId, expectedSequence, ex);
        }
    }

    @Override
    public List<OrderEvent> loadEvents(String aggregateId) {
        return jdbcTemplate.query(SELECT_SQL,
                (rs, rowNum) -> deserialize(rs.getString("event_type"), rs.getString("payload")),
                aggregateId);
    }

    private String resolveEventType(OrderEvent event) {
        return switch (event) {
            case OrderPlacedEvent ignored -> "OrderPlacedEvent";
            case OrderCancelledEvent ignored -> "OrderCancelledEvent";
        };
    }

    private Instant resolveOccurredAt(OrderEvent event) {
        return switch (event) {
            case OrderPlacedEvent e -> e.occurredAt();
            case OrderCancelledEvent e -> e.occurredAt();
        };
    }

    private String serialize(OrderEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new EventSerializationException("Failed to serialize event: " + event, ex);
        }
    }

    private OrderEvent deserialize(String eventType, String payload) {
        try {
            return switch (eventType) {
                case "OrderPlacedEvent" -> objectMapper.readValue(payload, OrderPlacedEvent.class);
                case "OrderCancelledEvent" -> objectMapper.readValue(payload, OrderCancelledEvent.class);
                default -> throw new EventSerializationException("Unknown event type: " + eventType);
            };
        } catch (JsonProcessingException ex) {
            throw new EventSerializationException("Failed to deserialize event of type " + eventType, ex);
        }
    }
}
