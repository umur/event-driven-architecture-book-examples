package com.umurinan.eda.ch06.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.umurinan.eda.ch06.projection.OrderDetailProjection;
import com.umurinan.eda.ch06.readmodel.OrderDetail;
import com.umurinan.eda.ch06.readmodel.OrderDetailRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDetailProjectionUnitTest {

    @Mock
    private OrderDetailRepository repository;

    private OrderDetailProjection projection;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        projection = new OrderDetailProjection(repository, objectMapper);
    }

    // --- ORDER_PLACED handling ---

    @Test
    void orderPlacedEvent_savesNewOrderDetail() throws Exception {
        var payload = buildPayload("ORDER_PLACED", """
                {
                  "orderId": "order-100",
                  "customerId": "cust-100",
                  "total": "250.00",
                  "occurredAt": "2024-06-15T08:00:00Z"
                }
                """);

        projection.consume(record(payload));

        var captor = ArgumentCaptor.forClass(OrderDetail.class);
        verify(repository).save(captor.capture());

        var saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo("order-100");
        assertThat(saved.getCustomerId()).isEqualTo("cust-100");
        assertThat(saved.getTotal()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(saved.getStatus()).isEqualTo("PLACED");
        assertThat(saved.getTrackingNumber()).isNull();
    }

    @Test
    void orderPlacedEvent_initialStatusIsPlaced() throws Exception {
        var payload = buildPayload("ORDER_PLACED", """
                {
                  "orderId": "order-101",
                  "customerId": "cust-101",
                  "total": "1.00",
                  "occurredAt": "2024-06-15T09:00:00Z"
                }
                """);

        projection.consume(record(payload));

        var captor = ArgumentCaptor.forClass(OrderDetail.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PLACED");
    }

    // --- ORDER_SHIPPED handling ---

    @Test
    void orderShippedEvent_updatesStatusAndTrackingNumber() throws Exception {
        var existing = new OrderDetail("order-200", "cust-200", new BigDecimal("300.00"), "PLACED");
        when(repository.findById("order-200")).thenReturn(Optional.of(existing));

        var payload = buildPayload("ORDER_SHIPPED", """
                {
                  "orderId": "order-200",
                  "trackingNumber": "TRACK-XYZ",
                  "occurredAt": "2024-06-16T10:00:00Z"
                }
                """);

        projection.consume(record(payload));

        var captor = ArgumentCaptor.forClass(OrderDetail.class);
        verify(repository).save(captor.capture());

        var saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("SHIPPED");
        assertThat(saved.getTrackingNumber()).isEqualTo("TRACK-XYZ");
    }

    @Test
    void orderShippedEvent_preservesExistingOrderData() throws Exception {
        var existing = new OrderDetail("order-201", "cust-201", new BigDecimal("500.00"), "PLACED");
        when(repository.findById("order-201")).thenReturn(Optional.of(existing));

        var payload = buildPayload("ORDER_SHIPPED", """
                {
                  "orderId": "order-201",
                  "trackingNumber": "TRACK-ABC",
                  "occurredAt": "2024-06-16T11:00:00Z"
                }
                """);

        projection.consume(record(payload));

        var captor = ArgumentCaptor.forClass(OrderDetail.class);
        verify(repository).save(captor.capture());

        var saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo("cust-201");
        assertThat(saved.getTotal()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void orderShippedEvent_whenOrderNotFound_doesNotSave() throws Exception {
        when(repository.findById("order-999")).thenReturn(Optional.empty());

        var payload = buildPayload("ORDER_SHIPPED", """
                {
                  "orderId": "order-999",
                  "trackingNumber": "TRACK-GHOST",
                  "occurredAt": "2024-06-16T12:00:00Z"
                }
                """);

        projection.consume(record(payload));

        verify(repository, never()).save(any());
    }

    // --- Unknown event types ---

    @Test
    void unknownEventType_doesNotInteractWithRepository() throws Exception {
        var payload = buildPayload("ORDER_CANCELLED", """
                { "orderId": "order-300" }
                """);

        projection.consume(record(payload));

        verify(repository, never()).save(any());
    }

    // --- Robustness ---

    @Test
    void malformedJson_doesNotThrow() {
        projection.consume(record("{ broken json ]"));

        verifyNoInteractions(repository);
    }

    @Test
    void orderPlacedFollowedByShipped_fullLifecycle() throws Exception {
        // Step 1: place the order
        var placedPayload = buildPayload("ORDER_PLACED", """
                {
                  "orderId": "order-lifecycle",
                  "customerId": "cust-lifecycle",
                  "total": "88.00",
                  "occurredAt": "2024-06-15T10:00:00Z"
                }
                """);
        projection.consume(record(placedPayload));

        var placedCaptor = ArgumentCaptor.forClass(OrderDetail.class);
        verify(repository).save(placedCaptor.capture());
        var placed = placedCaptor.getValue();
        assertThat(placed.getStatus()).isEqualTo("PLACED");

        // Step 2: ship the order — simulate the record that would have been saved
        var storedDetail = new OrderDetail("order-lifecycle", "cust-lifecycle", new BigDecimal("88.00"), "PLACED");
        when(repository.findById("order-lifecycle")).thenReturn(Optional.of(storedDetail));

        var shippedPayload = buildPayload("ORDER_SHIPPED", """
                {
                  "orderId": "order-lifecycle",
                  "trackingNumber": "TRACK-LC-001",
                  "occurredAt": "2024-06-16T09:00:00Z"
                }
                """);
        projection.consume(record(shippedPayload));

        var shippedCaptor = ArgumentCaptor.forClass(OrderDetail.class);
        // save is called twice total; we care about the second call
        verify(repository, org.mockito.Mockito.times(2)).save(shippedCaptor.capture());
        var allSaved = shippedCaptor.getAllValues();
        var shipped = allSaved.get(1);
        assertThat(shipped.getStatus()).isEqualTo("SHIPPED");
        assertThat(shipped.getTrackingNumber()).isEqualTo("TRACK-LC-001");
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
