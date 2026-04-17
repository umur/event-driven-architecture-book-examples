package com.umurinan.eda.ch06.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umurinan.eda.ch06.events.OrderPlacedEvent;
import com.umurinan.eda.ch06.events.OrderShippedEvent;
import com.umurinan.eda.ch06.readmodel.OrderDetail;
import com.umurinan.eda.ch06.readmodel.OrderDetailRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderDetailProjection {

    private static final Logger log = LoggerFactory.getLogger(OrderDetailProjection.class);

    private final OrderDetailRepository repository;
    private final ObjectMapper objectMapper;

    public OrderDetailProjection(OrderDetailRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "order-events",
            groupId = "order-detail-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record) {
        try {
            var root = objectMapper.readTree(record.value());
            var eventType = root.get("eventType").asText();
            var payload = root.get("payload");

            switch (eventType) {
                case "ORDER_PLACED" -> handleOrderPlaced(objectMapper.treeToValue(payload, OrderPlacedEvent.class));
                case "ORDER_SHIPPED" -> handleOrderShipped(objectMapper.treeToValue(payload, OrderShippedEvent.class));
                default -> log.warn("OrderDetailProjection: unknown event type '{}', skipping", eventType);
            }
        } catch (Exception ex) {
            log.error("OrderDetailProjection failed to process record at offset {}: {}",
                    record.offset(), ex.getMessage(), ex);
        }
    }

    private void handleOrderPlaced(OrderPlacedEvent event) {
        var detail = new OrderDetail(
                event.orderId(),
                event.customerId(),
                event.total(),
                "PLACED");
        repository.save(detail);
        log.debug("OrderDetailProjection: saved OrderDetail for order {}", event.orderId());
    }

    private void handleOrderShipped(OrderShippedEvent event) {
        repository.findById(event.orderId()).ifPresentOrElse(detail -> {
            detail.setTrackingNumber(event.trackingNumber());
            detail.setStatus("SHIPPED");
            repository.save(detail);
            log.debug("OrderDetailProjection: updated OrderDetail to SHIPPED for order {}", event.orderId());
        }, () -> log.warn("OrderDetailProjection: received ORDER_SHIPPED for unknown order {}", event.orderId()));
    }
}
