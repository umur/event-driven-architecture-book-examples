package com.umurinan.eda.ch04.commands;

import java.math.BigDecimal;

public record CreateProductCommand(
        String productId,
        String sellerId,
        String sellerName,
        String name,
        BigDecimal price
) {}
