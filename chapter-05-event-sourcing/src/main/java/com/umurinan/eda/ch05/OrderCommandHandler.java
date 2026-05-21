package com.umurinan.eda.ch05;

import com.umurinan.eda.ch05.commands.CancelOrderCommand;
import com.umurinan.eda.ch05.commands.PlaceOrderCommand;
import com.umurinan.eda.ch05.domain.OrderAggregate;
import com.umurinan.eda.ch05.domain.events.OrderCancelledEvent;
import com.umurinan.eda.ch05.domain.events.OrderPlacedEvent;
import com.umurinan.eda.ch05.store.EventStore;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OrderCommandHandler {

    private static final String TOPIC_ORDER_PLACED = "order-placed";
    private static final String TOPIC_ORDER_CANCELLED = "order-cancelled";

    private final EventStore eventStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderCommandHandler(EventStore eventStore, KafkaTemplate<String, Object> kafkaTemplate) {
        this.eventStore = eventStore;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void placeOrder(PlaceOrderCommand command) {
        var event = new OrderPlacedEvent(
                command.orderId(),
                command.customerId(),
                command.total(),
                Instant.now());

        eventStore.append(command.orderId(), event, 0L);
        kafkaTemplate.send(TOPIC_ORDER_PLACED, command.orderId(), event);
    }

    public void cancelOrder(CancelOrderCommand command) {
        var history = eventStore.loadEvents(command.orderId());
        var aggregate = OrderAggregate.rehydrate(command.orderId(), history);

        var event = new OrderCancelledEvent(command.orderId(), command.reason(), Instant.now());

        // next sequence number is the current event count
        long nextSequence = history.size();
        aggregate.apply(event); // validates the state transition before persisting
        eventStore.append(command.orderId(), event, nextSequence);
        kafkaTemplate.send(TOPIC_ORDER_CANCELLED, command.orderId(), event);
    }
}
