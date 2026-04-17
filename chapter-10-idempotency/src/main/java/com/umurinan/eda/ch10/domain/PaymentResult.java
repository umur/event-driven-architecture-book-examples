package com.umurinan.eda.ch10.domain;

import java.time.Instant;
import java.util.UUID;

public record PaymentResult(UUID transactionId, String status, Instant processedAt) {
}
