package com.umurinan.eda.ch14;

import com.umurinan.eda.ch14.events.OrderPlaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Content-based router: reads orders from orders-raw, inspects the total,
 * then forwards to the appropriate downstream topic.
 *
 * The routing logic itself lives in {@link OrderRoutingPredicate} — a pure
 * function — so it can be unit-tested independently of the Kafka wiring.
 */
@Service
public class OrderRouter {

    private static final Logger log = LoggerFactory.getLogger(OrderRouter.class);

    private final KafkaTemplate<String, OrderPlaced> kafkaTemplate;

    public OrderRouter(KafkaTemplate<String, OrderPlaced> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "orders-raw", groupId = "order-router")
    public void onOrder(OrderPlaced order, Acknowledgment ack) {
        var destination = OrderRoutingPredicate.determineDestination(order);
        kafkaTemplate.send(destination, order.orderId(), order);
        log.info("Routed order {} (total={}) to {}", order.orderId(), order.total(), destination);
        ack.acknowledge();
    }
}
