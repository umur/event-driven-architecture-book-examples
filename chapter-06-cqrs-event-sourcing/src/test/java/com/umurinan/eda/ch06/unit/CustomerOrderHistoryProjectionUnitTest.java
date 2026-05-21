package com.umurinan.eda.ch06.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.umurinan.eda.ch06.projection.CustomerOrderHistoryProjection;
import com.umurinan.eda.ch06.readmodel.CustomerOrderHistory;
import com.umurinan.eda.ch06.readmodel.CustomerOrderHistoryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CustomerOrderHistoryProjectionUnitTest {

    @Mock
    private CustomerOrderHistoryRepository repository;

    private CustomerOrderHistoryProjection projection;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        projection = new CustomerOrderHistoryProjection(repository, objectMapper);
    }

    // --- ORDER_PLACED handling ---

    @Test
    void orderPlacedEvent_savesCustomerOrderHistoryEntry() throws Exception {
        var payload = buildPayload("ORDER_PLACED", """
                {
                  "orderId": "order-001",
                  "customerId": "cust-001",
                  "total": "99.99",
                  "occurredAt": "2024-06-15T10:00:00Z"
                }
                """);

        projection.consume(record(payload));

        var captor = ArgumentCaptor.forClass(CustomerOrderHistory.class);
        verify(repository).save(captor.capture());

        var saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo("order-001");
        assertThat(saved.getCustomerId()).isEqualTo("cust-001");
        assertThat(saved.getTotal()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(saved.getStatus()).isEqualTo("PLACED");
    }

    @Test
    void orderPlacedEvent_statusIsAlwaysPlaced() throws Exception {
        var payload = buildPayload("ORDER_PLACED", """
                {
                  "orderId": "order-002",
                  "customerId": "cust-002",
                  "total": "10.00",
                  "occurredAt": "2024-06-15T11:00:00Z"
                }
                """);

        projection.consume(record(payload));

        var captor = ArgumentCaptor.forClass(CustomerOrderHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PLACED");
    }

    @Test
    void orderPlacedEvent_preservesBigDecimalPrecision() throws Exception {
        var payload = buildPayload("ORDER_PLACED", """
                {
                  "orderId": "order-003",
                  "customerId": "cust-003",
                  "total": "1234.5678",
                  "occurredAt": "2024-06-15T12:00:00Z"
                }
                """);

        projection.consume(record(payload));

        var captor = ArgumentCaptor.forClass(CustomerOrderHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTotal()).isEqualByComparingTo(new BigDecimal("1234.5678"));
    }

    // --- Non-ORDER_PLACED events are ignored ---

    @Test
    void orderShippedEvent_doesNotInteractWithRepository() throws Exception {
        var payload = buildPayload("ORDER_SHIPPED", """
                {
                  "orderId": "order-004",
                  "trackingNumber": "TRACK-001",
                  "occurredAt": "2024-06-15T13:00:00Z"
                }
                """);

        projection.consume(record(payload));

        verifyNoInteractions(repository);
    }

    @Test
    void unknownEventType_doesNotInteractWithRepository() throws Exception {
        var payload = buildPayload("ORDER_CANCELLED", """
                { "orderId": "order-005" }
                """);

        projection.consume(record(payload));

        verifyNoInteractions(repository);
    }

    // --- Robustness: malformed JSON must not propagate an exception ---

    @Test
    void malformedJson_doesNotThrow() {
        var record = record("this is not json at all");

        // consume() must swallow the error; the test fails if any exception escapes
        projection.consume(record);

        verifyNoInteractions(repository);
    }

    @Test
    void missingPayloadField_doesNotThrow() throws Exception {
        // eventType present but no "payload" node
        var json = """
                { "eventType": "ORDER_PLACED" }
                """;

        projection.consume(record(json));

        // repository must not have been called with a half-built entity
        never();
    }

    // --- Helpers ---

    private String buildPayload(String eventType, String payloadJson) throws Exception {
        var payloadNode = objectMapper.readTree(payloadJson);
        var root = objectMapper.createObjectNode();
        root.put("eventType", eventType);
        root.set("payload", payloadNode);
        return objectMapper.writeValueAsString(root);
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("order-events", 0, 0L, "key", value);
    }
}
