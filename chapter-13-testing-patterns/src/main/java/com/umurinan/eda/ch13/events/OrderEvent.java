package com.umurinan.eda.ch13.events;

import java.math.BigDecimal;

/**
 * Immutable event record representing a placed order.
 *
 * Using a Java record gives us equals/hashCode/toString for free and
 * makes the intent clear: this object is pure data, not behaviour.
 */
public record OrderEvent(String orderId, String customerId, BigDecimal total) {
}
