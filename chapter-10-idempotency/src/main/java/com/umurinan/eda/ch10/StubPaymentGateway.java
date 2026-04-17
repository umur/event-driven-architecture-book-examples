package com.umurinan.eda.ch10;

import com.umurinan.eda.ch10.domain.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
public class StubPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StubPaymentGateway.class);

    @Override
    public PaymentResult charge(String orderId, BigDecimal amount) {
        log.info("Charging {} for order {}", amount, orderId);
        return new PaymentResult(UUID.randomUUID(), "PROCESSED", Instant.now());
    }
}
