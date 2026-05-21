package com.umurinan.eda.ch14.consumers;

import com.umurinan.eda.ch14.events.OrderPlaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Downstream consumer for orders in the premium tier ($100–$999.99).
 * In a real system this might trigger priority processing or loyalty rewards.
 */
@Service
public class PremiumOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(PremiumOrderConsumer.class);

    @KafkaListener(topics = "orders-premium", groupId = "premium-order-consumer")
    public void onPremiumOrder(OrderPlaced order, Acknowledgment ack) {
        log.info("Premium order: {}", order.orderId());
        ack.acknowledge();
    }
}
