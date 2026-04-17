package com.umurinan.eda.ch10;

import com.umurinan.eda.ch10.domain.PaymentResult;
import com.umurinan.eda.ch10.domain.ProcessedEvent;
import com.umurinan.eda.ch10.domain.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class IdempotentPaymentHandler {

    private static final Logger log = LoggerFactory.getLogger(IdempotentPaymentHandler.class);

    private final ProcessedEventRepository processedEventRepository;
    private final PaymentGateway paymentGateway;

    public IdempotentPaymentHandler(ProcessedEventRepository processedEventRepository,
                                    PaymentGateway paymentGateway) {
        this.processedEventRepository = processedEventRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public PaymentResult processPayment(UUID idempotencyKey, String orderId, BigDecimal amount) {
        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(idempotencyKey)); // (1)
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate, skipping payment for idempotencyKey={}", idempotencyKey); // (2)
            return new PaymentResult(idempotencyKey, "ALREADY_PROCESSED", Instant.now());
        }

        return paymentGateway.charge(orderId, amount); // (3)
    }
}
