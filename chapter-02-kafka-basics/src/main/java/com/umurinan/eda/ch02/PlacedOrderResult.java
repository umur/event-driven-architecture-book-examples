package com.umurinan.eda.ch02;

import java.math.BigDecimal;
import java.time.Instant;

public record PlacedOrderResult(
        String orderId,
        BigDecimal total,
        Instant placedAt
) {}
