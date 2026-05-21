package com.umurinan.eda.ch05;

import com.umurinan.eda.ch05.domain.OrderAggregate;
import com.umurinan.eda.ch05.domain.OrderStatus;
import com.umurinan.eda.ch05.domain.events.OrderPlacedEvent;
import com.umurinan.eda.ch05.domain.events.OrderCancelledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for Event Sourcing snapshot pattern.
 *
 * Snapshots capture aggregate state at a point in time to avoid
 * re-processing all historical events. Critical for aggregates
 * with long event histories.
 */
@DisplayName("Snapshot Pattern Tests")
class SnapshotTest {

    @Test
    @DisplayName("snapshot: aggregate state can be captured")
    void snapshot_capturesState() {
        // Arrange: Create an aggregate with some state
        var orderId = "order-snapshot-001";
        var aggregate = new OrderAggregate(orderId);
        Instant now = Instant.now();
        
        var placedEvent = new OrderPlacedEvent(
            orderId,
            "customer-123",
            new BigDecimal("149.99"),
            now
        );
        aggregate.apply(placedEvent);
        
        // Act: Capture state (snapshot)
        var snapshot = new OrderSnapshot(
            aggregate.getOrderId(),
            aggregate.getStatus(),
            aggregate.getTotal(),
            1 // version after 1 event
        );
        
        // Assert: Snapshot contains current state
        assertThat(snapshot.orderId()).isEqualTo(orderId);
        assertThat(snapshot.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(snapshot.total()).isEqualByComparingTo(new BigDecimal("149.99"));
        assertThat(snapshot.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("snapshot: empty aggregate has version 0")
    void snapshot_emptyAggregate_hasVersionZero() {
        // Arrange
        var aggregate = new OrderAggregate("order-empty");
        
        // Act: Capture snapshot
        var snapshot = new OrderSnapshot(
            aggregate.getOrderId(),
            aggregate.getStatus(),
            BigDecimal.ZERO,
            0
        );
        
        // Assert
        assertThat(snapshot.version()).isZero();
        assertThat(snapshot.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("rehydration: aggregate can be rebuilt from events")
    void rehydration_fromEvents_producesCorrectState() {
        // Arrange
        var orderId = "order-rehydrate-001";
        Instant now = Instant.now();
        
        var event1 = new OrderPlacedEvent(orderId, "customer-1",
            new BigDecimal("100.00"), now);
        
        // Act: Rehydrate from events
        var aggregate = OrderAggregate.rehydrate(orderId, java.util.List.of(event1));
        
        // Assert
        assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(aggregate.getCustomerId()).isEqualTo("customer-1");
        assertThat(aggregate.getTotal()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("rehydration: empty event list produces pending aggregate")
    void rehydration_emptyList_producesPendingAggregate() {
        // Arrange & Act
        var aggregate = OrderAggregate.rehydrate("order-new", java.util.List.of());
        
        // Assert
        assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(aggregate.getOrderId()).isEqualTo("order-new");
    }

    @Test
    @DisplayName("cancellation: applied to placed order results in cancelled status")
    void cancellation_onPlacedOrder_resultsInCancelled() {
        // Arrange
        var orderId = "order-cancel-001";
        var aggregate = new OrderAggregate(orderId);
        Instant now = Instant.now();
        
        aggregate.apply(new OrderPlacedEvent(orderId, "customer-1",
            new BigDecimal("50.00"), now));
        
        // Act: Apply cancellation
        aggregate.apply(new OrderCancelledEvent(orderId, "customer request", now));
        
        // Assert
        assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancellation: on non-placed order throws exception")
    void cancellation_onNonPlacedOrder_throwsException() {
        // Arrange: Create aggregate but don't place order
        var aggregate = new OrderAggregate("order-invalid-cancel");
        Instant now = Instant.now();
        
        // Act & Assert
        assertThatThrownBy(() -> 
            aggregate.apply(new OrderCancelledEvent("order-invalid-cancel", "test", now))
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("PLACED");
    }

    // Record for snapshot data
    public record OrderSnapshot(String orderId, OrderStatus status, BigDecimal total, long version) {}
}
