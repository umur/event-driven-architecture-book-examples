package com.umurinan.eda.ch05;

import com.umurinan.eda.ch05.domain.OrderAggregate;
import com.umurinan.eda.ch05.domain.OrderStatus;
import com.umurinan.eda.ch05.domain.events.OrderCancelledEvent;
import com.umurinan.eda.ch05.domain.events.OrderPlacedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderAggregateTest {

    private static final String ORDER_ID = "order-001";
    private static final String CUSTOMER_ID = "customer-42";

    @Test
    void newAggregate_hasStatusPendingAndNullTotal() {
        var aggregate = new OrderAggregate(ORDER_ID);

        assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(aggregate.getTotal()).isNull();
        assertThat(aggregate.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(aggregate.getCustomerId()).isNull();
    }

    @Test
    void applyOrderPlacedEvent_setsStatusToPlacedAndTotal() {
        var aggregate = new OrderAggregate(ORDER_ID);
        var event = new OrderPlacedEvent(ORDER_ID, CUSTOMER_ID, new BigDecimal("149.99"), Instant.now());

        aggregate.apply(event);

        assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(aggregate.getTotal()).isEqualByComparingTo(new BigDecimal("149.99"));
        assertThat(aggregate.getCustomerId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    void applyOrderCancelledEvent_afterPlaced_setsStatusToCancelled() {
        var aggregate = new OrderAggregate(ORDER_ID);
        aggregate.apply(new OrderPlacedEvent(ORDER_ID, CUSTOMER_ID, new BigDecimal("99.00"), Instant.now()));

        aggregate.apply(new OrderCancelledEvent(ORDER_ID, "customer changed mind", Instant.now()));

        assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void applyOrderCancelledEvent_onPendingAggregate_throwsIllegalStateException() {
        var aggregate = new OrderAggregate(ORDER_ID);
        var cancelEvent = new OrderCancelledEvent(ORDER_ID, "too eager", Instant.now());

        assertThatThrownBy(() -> aggregate.apply(cancelEvent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLACED");
    }

    @Test
    void rehydrate_fromTwoEvents_producesCorrectFinalState() {
        var placed = new OrderPlacedEvent(ORDER_ID, CUSTOMER_ID, new BigDecimal("250.00"), Instant.now());
        var cancelled = new OrderCancelledEvent(ORDER_ID, "duplicate order", Instant.now());

        var aggregate = OrderAggregate.rehydrate(ORDER_ID, List.of(placed, cancelled));

        assertThat(aggregate.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(aggregate.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(aggregate.getTotal()).isEqualByComparingTo(new BigDecimal("250.00"));
    }
}
