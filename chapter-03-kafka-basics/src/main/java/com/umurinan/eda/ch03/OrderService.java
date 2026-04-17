package com.umurinan.eda.ch03;

import com.umurinan.eda.ch03.commands.PlaceOrderCommand;
import com.umurinan.eda.ch03.events.OrderPlaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    static final String ORDER_PLACED_TOPIC = "order-placed";

    private final KafkaTemplate<String, OrderPlaced> kafkaTemplate;

    public OrderService(KafkaTemplate<String, OrderPlaced> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public PlacedOrderResult placeOrder(PlaceOrderCommand command) {
        var placedAt = Instant.now();
        var event = new OrderPlaced(
                command.orderId(),
                command.customerId(),
                command.total(),
                placedAt
        );

        try {
            var result = kafkaTemplate.send(ORDER_PLACED_TOPIC, command.orderId(), event)
                    .get(5, TimeUnit.SECONDS);                                    // (1)
            log.info("Published OrderPlaced event for orderId={} to partition={} offset={}",
                    command.orderId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (ExecutionException e) {
            throw new OrderProcessingException(                                   // (2)
                    "Kafka send failed for orderId=" + command.orderId(), e.getCause());
        } catch (TimeoutException e) {
            throw new OrderProcessingException(                                   // (3)
                    "Kafka unavailable — send timed out for orderId=" + command.orderId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderProcessingException(                                   // (4)
                    "Send interrupted for orderId=" + command.orderId(), e);
        }

        return new PlacedOrderResult(command.orderId(), command.total(), placedAt);
    }
}
