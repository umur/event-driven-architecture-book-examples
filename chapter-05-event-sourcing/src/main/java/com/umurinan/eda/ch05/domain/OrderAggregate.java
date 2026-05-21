package com.umurinan.eda.ch05.domain;

import com.umurinan.eda.ch05.domain.events.OrderCancelledEvent;
import com.umurinan.eda.ch05.domain.events.OrderEvent;
import com.umurinan.eda.ch05.domain.events.OrderPlacedEvent;

import java.math.BigDecimal;
import java.util.List;

public class OrderAggregate {

    private final String orderId;
    private String customerId;
    private OrderStatus status;
    private BigDecimal total;

    public OrderAggregate(String orderId) {
        this.orderId = orderId;
        this.status = OrderStatus.PENDING;
    }

    public void apply(OrderEvent event) {
        switch (event) {
            case OrderPlacedEvent e -> apply(e);
            case OrderCancelledEvent e -> apply(e);
        }
    }

    private void apply(OrderPlacedEvent event) {
        this.customerId = event.customerId();
        this.status = OrderStatus.PLACED;
        this.total = event.total();
    }

    private void apply(OrderCancelledEvent event) {
        if (this.status != OrderStatus.PLACED) {
            throw new IllegalStateException(
                    "Cannot cancel an order that is not in PLACED status. Current status: " + this.status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public static OrderAggregate rehydrate(String orderId, List<OrderEvent> events) {
        var aggregate = new OrderAggregate(orderId);
        for (var event : events) {
            aggregate.apply(event);
        }
        return aggregate;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
