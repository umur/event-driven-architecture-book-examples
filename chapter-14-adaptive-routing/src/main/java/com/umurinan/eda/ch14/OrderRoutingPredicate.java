package com.umurinan.eda.ch14;

import com.umurinan.eda.ch14.events.OrderPlaced;

import java.math.BigDecimal;

/**
 * Pure routing function: given an order, return the destination topic name.
 *
 * This is intentionally a static utility — no Spring beans, no side effects,
 * no I/O.  That makes it trivially unit-testable without standing up any
 * infrastructure.  All the interesting boundary cases live in
 * OrderRoutingPredicateTest.
 *
 * Routing rules:
 *   total >= 1000  →  orders-high-value
 *   total >=  100  →  orders-premium
 *   else           →  orders-standard
 */
public final class OrderRoutingPredicate {

    public static final String TOPIC_HIGH_VALUE = "orders-high-value";
    public static final String TOPIC_PREMIUM    = "orders-premium";
    public static final String TOPIC_STANDARD   = "orders-standard";

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal PREMIUM_THRESHOLD    = new BigDecimal("100");

    // Non-instantiable utility class.
    private OrderRoutingPredicate() {
    }

    public static String determineDestination(OrderPlaced order) {
        if (order.total().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            return TOPIC_HIGH_VALUE;
        }
        if (order.total().compareTo(PREMIUM_THRESHOLD) >= 0) {
            return TOPIC_PREMIUM;
        }
        return TOPIC_STANDARD;
    }
}
