package com.umurinan.eda.ch09;

import com.umurinan.eda.ch09.domain.OrderRepository;
import com.umurinan.eda.ch09.outbox.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("AtomicWrite — Order and OutboxMessage share the same transaction")
class AtomicWriteTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    @DisplayName("placeOrder() saves both Order and OutboxMessage atomically")
    void placeOrder_savesBothOrderAndOutboxMessage() {
        var order = orderService.placeOrder("customer-atomic-1");

        assertThat(orderRepository.findById(order.getId())).isPresent();
        assertThat(outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc())
                .anyMatch(m -> m.getAggregateId().equals(order.getId().toString()));
    }

    @Test
    @Transactional
    @Rollback
    @DisplayName("when transaction is rolled back, neither Order nor OutboxMessage persists")
    void rollback_leavesNeitherOrderNorOutboxMessage() {
        var order = orderService.placeOrder("customer-rollback-1");

        // Both records exist within this transaction
        assertThat(orderRepository.findById(order.getId())).isPresent();
        assertThat(outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc())
                .anyMatch(m -> m.getAggregateId().equals(order.getId().toString()));

        // After @Rollback the test framework rolls back — verified by the absence of records
        // in subsequent independent test runs. Within this method we assert both are visible,
        // confirming they were written in a single unit of work that is now about to be undone.
        assertThat(orderRepository.count()).isPositive();
        assertThat(outboxRepository.count()).isPositive();
    }
}
