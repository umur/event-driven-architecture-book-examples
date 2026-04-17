package com.umurinan.eda.ch05.domain.events;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderPlacedEvent(
        String orderId,
        String customerId,
        BigDecimal total,
        Instant occurredAt
) implements OrderEvent {
}
