package com.umurinan.eda.ch06.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umurinan.eda.ch06.events.OrderPlacedEvent;
import com.umurinan.eda.ch06.readmodel.DailyOrderSummary;
import com.umurinan.eda.ch06.readmodel.DailyOrderSummaryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class OrderAnalyticsProjection {

    private static final Logger log = LoggerFactory.getLogger(OrderAnalyticsProjection.class);

    private final DailyOrderSummaryRepository repository;
    private final ObjectMapper objectMapper;

    public OrderAnalyticsProjection(DailyOrderSummaryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "order-events",
            groupId = "analytics-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            var root = objectMapper.readTree(record.value());
            var eventType = root.get("eventType").asText();

            if ("ORDER_PLACED".equals(eventType)) {
                var payload = root.get("payload");
                var event = objectMapper.treeToValue(payload, OrderPlacedEvent.class);
                handleOrderPlaced(event);
            } else {
                log.debug("OrderAnalyticsProjection: ignoring event type '{}'", eventType);
            }
        } catch (Exception ex) {
            log.error("OrderAnalyticsProjection failed to process record at offset {}: {}",
                    record.offset(), ex.getMessage(), ex);
        }
    }

    private void handleOrderPlaced(OrderPlacedEvent event) {
        var date = event.occurredAt() != null
                ? event.occurredAt().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                : LocalDate.now();

        var summary = repository.findById(date)
                .orElseGet(() -> new DailyOrderSummary(date));

        summary.incrementOrder(event.total());
        repository.save(summary);

        log.debug("OrderAnalyticsProjection: updated daily summary for {} — count={}, revenue={}",
                date, summary.getOrderCount(), summary.getTotalRevenue());
    }
}
