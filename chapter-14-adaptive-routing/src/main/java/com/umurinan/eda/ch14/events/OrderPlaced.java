package com.umurinan.eda.ch14.events;

import java.math.BigDecimal;

/**
 * Immutable event record representing an order that has been placed and is
 * waiting to be routed to the appropriate processing pipeline.
 *
 * The record's structural equality is useful in tests: two OrderPlaced
 * instances with the same field values are equal without extra ceremony.
 */
public record OrderPlaced(String orderId, String customerId, BigDecimal total) {
}
