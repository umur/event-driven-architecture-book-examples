package com.umurinan.eda.ch10;

import com.umurinan.eda.ch10.events.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class KafkaPaymentListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentListener.class);

    private final IdempotentPaymentHandler idempotentPaymentHandler;

    public KafkaPaymentListener(IdempotentPaymentHandler idempotentPaymentHandler) {
        this.idempotentPaymentHandler = idempotentPaymentHandler;
    }

    @KafkaListener(topics = "payment-requests", groupId = "payment-service")
    public void onPaymentRequest(PaymentRequest event, Acknowledgment ack) {
        log.info("Received PaymentRequest idempotencyKey={} orderId={}", event.idempotencyKey(), event.orderId());
        idempotentPaymentHandler.processPayment(event.idempotencyKey(), event.orderId(), event.amount());
        ack.acknowledge();
    }
}
