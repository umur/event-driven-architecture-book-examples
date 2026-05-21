package com.umurinan.eda.ch05.commands;

public record CancelOrderCommand(
        String orderId,
        String reason
) {
}
