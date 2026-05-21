package com.umurinan.eda.ch09;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umurinan.eda.ch09.domain.Order;
import com.umurinan.eda.ch09.domain.OrderRepository;
import com.umurinan.eda.ch09.events.OrderPlaced;
import com.umurinan.eda.ch09.outbox.OutboxMessage;
import com.umurinan.eda.ch09.outbox.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxRepository outboxRepository,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order placeOrder(String customerId) {
        var order = orderRepository.save(new Order(customerId));

        var event = new OrderPlaced(
                order.getId().toString(),
                customerId,
                order.getCreatedAt()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OrderPlaced event", e);
        }

        outboxRepository.save(new OutboxMessage(
                "Order",
                order.getId().toString(),
                "OrderPlaced",
                payload
        ));

        return order;
    }
}
