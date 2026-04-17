package com.umurinan.eda.ch14.consumers;

import com.umurinan.eda.ch14.events.OrderPlaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Downstream consumer for orders below the premium threshold.
 * In a real system this would invoke a standard fulfilment workflow.
 */
@Service
public class StandardOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(StandardOrderConsumer.class);

    @KafkaListener(topics = "orders-standard", groupId = "standard-order-consumer")
    public void onStandardOrder(OrderPlaced order, Acknowledgment ack) {
        log.info("Standard order: {}", order.orderId());
        ack.acknowledge();
    }
}
