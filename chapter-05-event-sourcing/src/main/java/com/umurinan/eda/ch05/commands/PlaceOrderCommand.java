package com.umurinan.eda.ch05.commands;

import java.math.BigDecimal;

public record PlaceOrderCommand(
        String orderId,
        String customerId,
        BigDecimal total
) {
}
