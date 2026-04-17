package com.umurinan.eda.ch03.commands;

import java.math.BigDecimal;

public record PlaceOrderCommand(
        String orderId,
        String customerId,
        BigDecimal total
) {}
