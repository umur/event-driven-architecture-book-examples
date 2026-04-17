package com.umurinan.eda.ch04.events;

import java.math.BigDecimal;

public record ProductPriceUpdated(
        String productId,
        BigDecimal newPrice
) {}
