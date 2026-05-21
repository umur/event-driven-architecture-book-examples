package com.umurinan.eda.ch04.events;

import java.math.BigDecimal;

public record ProductCreated(
        String productId,
        String sellerId,
        String sellerName,
        String name,
        BigDecimal price
) {}
