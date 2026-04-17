package com.umurinan.eda.ch14;

import com.umurinan.eda.ch14.events.OrderPlaced;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.umurinan.eda.ch14.OrderRoutingPredicate.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test for the routing predicate — zero Spring context, zero Kafka.
 *
 * This is the fastest test in the chapter: it runs in milliseconds and gives
 * immediate feedback on boundary conditions. Because OrderRoutingPredicate is
 * a pure function (no I/O, no dependencies) we can test every boundary value
 * without standing up any infrastructure.
 *
 * Boundary value analysis for three tiers:
 *
 *   orders-standard  : [0, 100)
 *   orders-premium   : [100, 1000)
 *   orders-high-value: [1000, ∞)
 *
 * Testing the exact boundary values (99.99, 100.00, 999.99, 1000.00) is more
 * valuable than testing mid-range values. Off-by-one errors in compareTo()
 * calls are the most common routing bug.
 */
@DisplayName("OrderRoutingPredicate: routes orders to correct topic based on total")
class OrderRoutingPredicateTest {

    // -- Standard tier boundary tests ----------------------------------------

    @Test
    @DisplayName("total = 0.00 → orders-standard (zero-value order)")
    void zeroTotalRoutesToStandard() {
        var order = order("0.00");
        assertThat(determineDestination(order)).isEqualTo(TOPIC_STANDARD);
    }

    @Test
    @DisplayName("total = 50.00 → orders-standard (mid-range standard)")
    void midStandardRoutesToStandard() {
        var order = order("50.00");
        assertThat(determineDestination(order)).isEqualTo(TOPIC_STANDARD);
    }

    @Test
    @DisplayName("total = 99.99 → orders-standard (just below premium threshold)")
    void justBelowPremiumThresholdRoutesToStandard() {
        var order = order("99.99");
        assertThat(determineDestination(order)).isEqualTo(TOPIC_STANDARD);
    }

    // -- Premium tier boundary tests -----------------------------------------

    @Test
    @DisplayName("total = 100.00 → orders-premium (exactly at premium threshold)")
    void exactlyAtPremiumThresholdRoutesToPremium() {
        var order = order("100.00");
        assertThat(determineDestination(order)).isEqualTo(TOPIC_PREMIUM);
    }

    @Test
    @DisplayName("total = 100.01 → orders-premium (just above premium threshold)")
    void justAbovePremiumThresholdRoutesToPremium() {
        var order = order("100.01");
        assertThat(determineDestination(order)).isEqualTo(TOPIC_PREMIUM);
    }

    @Test
    @DisplayName("total = 999.99 → orders-premium (just below high-value threshold)")
    void justBelowHighValueThresholdRoutesToPremium() {
        var order = order("999.99");
        assertThat(determineDestination(order)).isEqualTo(TOPIC_PREMIUM);
    }

    // -- High-value tier boundary tests --------------------------------------

    @Test
    @DisplayName("total = 1000.00 → orders-high-value (exactly at high-value threshold)")
    void exactlyAtHighValueThresholdRoutesToHighValue() {
        var order = order("1000.00");
        assertThat(determineDestination(order)).isEqualTo(TOPIC_HIGH_VALUE);
    }

    @Test
    @DisplayName("total = 1000.01 → orders-high-value (just above high-value threshold)")
    void justAboveHighValueThresholdRoutesToHighValue() {
        var order = order("1000.01");
        assertThat(determineDestination(order)).isEqualTo(TOPIC_HIGH_VALUE);
    }

    @Test
    @DisplayName("total = 50000.00 → orders-high-value (large order well above threshold)")
    void largeOrderRoutesToHighValue() {
        var order = order("50000.00");
        assertThat(determineDestination(order)).isEqualTo(TOPIC_HIGH_VALUE);
    }

    // -- Helper --------------------------------------------------------------

    private static OrderPlaced order(String total) {
        return new OrderPlaced("order-test", "customer-test", new BigDecimal(total));
    }
}
