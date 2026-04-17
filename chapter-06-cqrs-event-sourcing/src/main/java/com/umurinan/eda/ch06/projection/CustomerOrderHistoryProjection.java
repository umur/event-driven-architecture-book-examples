package com.umurinan.eda.ch06.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umurinan.eda.ch06.events.OrderPlacedEvent;
import com.umurinan.eda.ch06.readmodel.CustomerOrderHistory;
import com.umurinan.eda.ch06.readmodel.CustomerOrderHistoryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class CustomerOrderHistoryProjection {

    private static final Logger log = LoggerFactory.getLogger(CustomerOrderHistoryProjection.class);

    private final CustomerOrderHistoryRepository repository;
    private final ObjectMapper objectMapper;

    public CustomerOrderHistoryProjection(CustomerOrderHistoryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "order-events",
            groupId = "customer-history-service",
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
                log.debug("CustomerOrderHistoryProjection: ignoring event type '{}'", eventType);
            }
        } catch (Exception ex) {
            log.error("CustomerOrderHistoryProjection failed to process record at offset {}: {}",
                    record.offset(), ex.getMessage(), ex);
        }
    }

    private void handleOrderPlaced(OrderPlacedEvent event) {
        var entry = new CustomerOrderHistory(
                event.customerId(),
                event.orderId(),
                event.total(),
                "PLACED");
        repository.save(entry);
        log.debug("CustomerOrderHistoryProjection: recorded order {} for customer {}",
                event.orderId(), event.customerId());
    }
}
