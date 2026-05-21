package com.umurinan.eda.ch12;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes order-placed events to the {@code order-events} topic.
 *
 * Each publish starts a dedicated span so the trace context is current when
 * {@link KafkaTemplate#send} is called.  Spring Kafka's
 * {@code KafkaTemplateObservation} (wired automatically via
 * {@code micrometer-tracing-bridge-otel}) then injects the W3C
 * {@code traceparent} header into the outgoing Kafka record.
 */
@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String TOPIC = "order-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Tracer tracer;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate, Tracer tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.tracer = tracer;
    }

    public void publishOrderPlaced(String orderId) {
        var span = tracer.nextSpan().name("order.placed.publish").start();

        try (var ws = tracer.withSpan(span)) {
            log.info("Publishing order event: orderId={} traceId={}",
                    orderId, span.context().traceId());
            kafkaTemplate.send(TOPIC, orderId,
                    "{\"orderId\":\"" + orderId + "\"}");
        } finally {
            span.end();
        }
    }
}
