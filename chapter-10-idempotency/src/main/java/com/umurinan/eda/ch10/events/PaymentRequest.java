package com.umurinan.eda.ch10.events;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(UUID idempotencyKey, String orderId, BigDecimal amount) {
}
