package com.umurinan.eda.ch07.commands;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sent to "payment-commands" when the SAGA starts.
 * The payment service replies on "payment-replies" with a {@code PaymentReply}.
 */
public record ProcessPaymentCommand(
        String sagaId,
        String orderId,
        BigDecimal amount,
        UUID idempotencyKey
) {}
