package com.umurinan.eda.ch02.commands;

import java.math.BigDecimal;

public record PlaceOrderCommand(
        String orderId,
        String customerId,
        BigDecimal total
) {}
