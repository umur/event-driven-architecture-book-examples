package com.umurinan.eda.ch05.domain.events;

import java.time.Instant;

public record OrderCancelledEvent(
        String orderId,
        String reason,
        Instant occurredAt
) implements OrderEvent {
}
