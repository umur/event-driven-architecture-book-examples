package com.umurinan.eda.ch06.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.umurinan.eda.ch06.projection.OrderAnalyticsProjection;
import com.umurinan.eda.ch06.readmodel.DailyOrderSummary;
import com.umurinan.eda.ch06.readmodel.DailyOrderSummaryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAnalyticsProjectionUnitTest {

    @Mock
    private DailyOrderSummaryRepository repository;

    private OrderAnalyticsProjection projection;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        projection = new OrderAnalyticsProjection(repository, objectMapper);
    }

    // --- ORDER_PLACED: new day (no existing summary) ---

    @Test
    void orderPlacedEvent_whenNoPriorSummary_createsNewSummaryWithOneOrder() throws Exception {
        var date = LocalDate.of(2024, 6, 15);
        when(repository.findById(date)).thenReturn(Optional.empty());

        var payload = buildPayload("ORDER_PLACED", """
                {
                  "orderId": "order-A1",
                  "customerId": "cust-A1",
                  "total": "150.00",
                  "occurredAt": "2024-06-15T10:00:00Z"
                }
                """);

        projection.consume(record(payload));

        var captor = ArgumentCaptor.forClass(DailyOrderSummary.class);
        verify(repository).save(captor.capture());

        var saved = captor.getValue();
        assertThat(saved.getSummaryDate()).isEqualTo(date);
        assertThat(saved.getOrderCount()).isEqualTo(1);
        assertThat(saved.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    // --- ORDER_PLACED: existing summary gets incremented ---

    @Test
    void orderPlacedEvent_whenSummaryExists_incrementsCountAndRevenue() throws Exception {
        var date = LocalDate.of(2024, 6, 15);
        var existing = new DailyOrderSummary(date);
        existing.incrementOrder(new BigDecimal("200.00")); // already has 1 order

        when(repository.findById(date)).thenReturn(Optional.of(existing));

        var payload = buildPayload("ORDER_PLACED", """
                {
                  "orderId": "order-A2",
                  "customerId": "cust-A2",
                  "total": "50.00",
                  "occurredAt": "2024-06-15T11:00:00Z"
                }
                """);

        projection.consume(record(payload));

        var captor = ArgumentCaptor.forClass(DailyOrderSummary.class);
        verify(repository).save(captor.capture());

        var saved = captor.getValue();
        assertThat(saved.getOrderCount()).isEqualTo(2);
        assertThat(saved.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    // --- Date derivation from occurredAt ---

    @Test
    void orderPlacedEvent_dateIsDerivedFromOccurredAt() throws Exception {
        // occurredAt is in UTC — projection must derive 2024-03-10
        var expectedDate = LocalDate.of(2024, 3, 10);
        when(repository.findById(expectedDate)).thenReturn(Optional.empty());

        var payload = buildPayload("ORDER_PLACED", """
                {
                  "orderId": "order-A3",
                  "customerId": "cust-A3",
                  "total": "75.00",
                  "occurredAt": "2024-03-10T23:59:59Z"
                }
                """);

        projection.consume(record(payload));

        var captor = ArgumentCaptor.forClass(DailyOrderSummary.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSummaryDate()).isEqualTo(expectedDate);
    }

    @Test
    void orderPlacedEvent_withNullOccurredAt_fallsBackToToday() throws Exception {
        // When occurredAt is null the projection falls back to LocalDate.now()
        when(repository.findById(any())).thenReturn(Optional.empty());

        var payload = buildPayload("ORDER_PLACED", """
                {
                  "orderId": "order-A4",
                  "customerId": "cust-A4",
                  "total": "10.00",
                  "occurredAt": null
                }
                """);

        projection.consume(record(payload));

        // We can't pin down the exact date without mocking the clock, so just verify
        // that a save was attempted (meaning the projection didn't blow up).
        verify(repository).save(any(DailyOrderSummary.class));
    }

    // --- ORDER_SHIPPED is ignored ---

    @Test
    void orderShippedEvent_doesNotInteractWithRepository() throws Exception {
        var payload = buildPayload("ORDER_SHIPPED", """
                {
                  "orderId": "order-B1",
                  "trackingNumber": "TRACK-001",
                  "occurredAt": "2024-06-16T09:00:00Z"
                }
                """);

        projection.consume(record(payload));

        verifyNoInteractions(repository);
    }

    // --- Unknown events are ignored ---

    @Test
    void unknownEventType_doesNotInteractWithRepository() throws Exception {
        var payload = buildPayload("ORDER_REFUNDED", """
                { "orderId": "order-C1", "amount": "30.00" }
                """);

        projection.consume(record(payload));

        verifyNoInteractions(repository);
    }

    // --- Robustness ---

    @Test
    void malformedJson_doesNotThrow() {
        projection.consume(record("not json"));

        verifyNoInteractions(repository);
    }

    @Test
    void multipleOrdersOnSameDay_eachIncrementsSummary() throws Exception {
        var date = LocalDate.of(2024, 6, 20);
        var summary = new DailyOrderSummary(date);

        // Repository returns the same mutable summary on every call so increments accumulate
        when(repository.findById(date)).thenReturn(Optional.of(summary));

        for (int i = 1; i <= 3; i++) {
            var payload = buildPayload("ORDER_PLACED",
                    String.format("""
                            {
                              "orderId": "order-multi-%d",
                              "customerId": "cust-multi",
                              "total": "100.00",
                              "occurredAt": "2024-06-20T0%d:00:00Z"
                            }
                            """, i, i));
            projection.consume(record(payload));
        }

        assertThat(summary.getOrderCount()).isEqualTo(3);
        assertThat(summary.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("300.00"));
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
