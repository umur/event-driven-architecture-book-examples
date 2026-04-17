package com.umurinan.eda.ch03.events;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderPlaced(
        String orderId,
        String customerId,
        BigDecimal total,
        Instant placedAt
) {}
