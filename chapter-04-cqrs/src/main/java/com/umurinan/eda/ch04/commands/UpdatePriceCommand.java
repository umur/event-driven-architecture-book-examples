package com.umurinan.eda.ch04.commands;

import java.math.BigDecimal;

public record UpdatePriceCommand(
        String productId,
        BigDecimal newPrice
) {}
