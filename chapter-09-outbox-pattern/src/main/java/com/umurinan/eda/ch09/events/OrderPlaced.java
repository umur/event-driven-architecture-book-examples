package com.umurinan.eda.ch09.events;

import java.time.Instant;

public record OrderPlaced(String orderId, String customerId, Instant createdAt) {
}
