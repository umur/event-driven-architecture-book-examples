package com.umurinan.eda.ch12;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test helper: listens on {@code order-events} and captures the raw
 * {@code traceparent} header so assertions can verify trace-ID propagation.
 *
 * Declared as a top-level class so that {@code @SpringBootTest} can
 * discover it via {@code @Import} on the test class.
 */
@Component
public class TestTraceCapture {

    private final AtomicReference<String> capturedTraceparent = new AtomicReference<>();

    public void reset() {
        capturedTraceparent.set(null);
    }

    public String capturedTraceparent() {
        return capturedTraceparent.get();
    }

    @KafkaListener(
            topics = "order-events-trace-test",
            groupId = "trace-propagation-test-consumer"
    )
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        var header = record.headers().lastHeader("traceparent");
        if (header != null) {
            capturedTraceparent.set(
                    new String(header.value(), StandardCharsets.UTF_8));
        }
        ack.acknowledge();
    }
}
