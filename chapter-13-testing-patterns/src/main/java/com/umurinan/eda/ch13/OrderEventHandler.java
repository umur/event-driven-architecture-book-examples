package com.umurinan.eda.ch13;

import com.umurinan.eda.ch13.events.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal order event handler whose sole responsibility is to demonstrate
 * testable Kafka consumer patterns.
 *
 * The processedOrders map is intentionally package-visible so tests can
 * inject the handler and inspect state without reflection gymnastics.
 */
@Service
public class OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);

    // Exposed for test assertions — stores the last-seen event per orderId.
    final ConcurrentHashMap<String, OrderEvent> processedOrders = new ConcurrentHashMap<>();

    @KafkaListener(topics = "orders", groupId = "order-handler")
    public void onOrder(OrderEvent event, Acknowledgment ack) {
        if (event.orderId() == null) {
            // A null orderId is a non-recoverable data error.
            // Throwing here causes Spring Kafka's DefaultErrorHandler to route
            // the message to orders.DLT after exhausting retries.
            throw new IllegalArgumentException("orderId must not be null");
        }

        if (processedOrders.containsKey(event.orderId())) {
            // Idempotency guard: we have already processed this order.
            // Acknowledging without re-processing demonstrates at-most-once
            // semantics on top of at-least-once delivery.
            log.info("Duplicate, skipping orderId={}", event.orderId());
            ack.acknowledge();
            return;
        }

        processedOrders.put(event.orderId(), event);
        log.info("Processed order: {}", event.orderId());
        ack.acknowledge();
    }
}
