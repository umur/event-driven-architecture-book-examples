package com.umurinan.eda.ch14.consumers;

import com.umurinan.eda.ch14.events.OrderPlaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Downstream consumer for high-value orders ($1000+).
 * In a real system this might route to a dedicated VIP fulfilment queue
 * or trigger a manual review step.
 */
@Service
public class HighValueOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(HighValueOrderConsumer.class);

    @KafkaListener(topics = "orders-high-value", groupId = "high-value-order-consumer")
    public void onHighValueOrder(OrderPlaced order, Acknowledgment ack) {
        log.info("High-value order: {}", order.orderId());
        ack.acknowledge();
    }
}
