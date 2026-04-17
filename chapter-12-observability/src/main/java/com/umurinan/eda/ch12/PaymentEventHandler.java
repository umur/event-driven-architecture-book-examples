package com.umurinan.eda.ch12;

import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Simulates the payment service consuming order events.
 *
 * Spring Boot auto-configures {@code KafkaListenerObservation} when
 * {@code micrometer-tracing-bridge-otel} is on the classpath.  Before this
 * method is invoked the bridge extracts the W3C {@code traceparent} header
 * from the record and restores the trace context, so
 * {@link Tracer#currentSpan()} returns a span that belongs to the same trace
 * as the publisher.
 */
@Service
public class PaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);

    private final Tracer tracer;

    public PaymentEventHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    @KafkaListener(topics = "order-events", groupId = "payment-service")
    public void onOrderEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        var currentSpan = tracer.currentSpan();
        var traceId = currentSpan != null ? currentSpan.context().traceId() : "none";

        log.info("Processing order event. TraceId={} payload={}", traceId, record.value());

        ack.acknowledge();
    }
}
