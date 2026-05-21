package com.umurinan.eda.ch07.commands;

import java.util.UUID;

/**
 * Compensation command sent to "payment-commands" when inventory reservation fails.
 * Reverses the payment that was already processed so money is not held.
 */
public record RefundPaymentCommand(
        String sagaId,
        String orderId,
        String transactionId,
        UUID idempotencyKey
) {}
