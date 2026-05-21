package com.umurinan.eda.ch07.replies;

/**
 * Published to "payment-replies" by the payment participant service.
 *
 * @param sagaId        correlates back to the {@code OrderSaga} id
 * @param success       true when the charge went through
 * @param transactionId populated only on success; the payment provider's reference
 * @param errorMessage  populated only on failure; human-readable reason
 */
public record PaymentReply(
        String sagaId,
        boolean success,
        String transactionId,
        String errorMessage
) {}
