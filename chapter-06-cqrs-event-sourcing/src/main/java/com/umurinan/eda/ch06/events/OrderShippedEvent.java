package com.umurinan.eda.ch06.events;

import java.time.Instant;

public record OrderShippedEvent(
        String orderId,
        String trackingNumber,
        Instant occurredAt
) {
}
