package com.umurinan.eda.ch12;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test helper: listens on {@code trace-header-test} and captures the raw
 * {@code traceparent} header value so assertions can verify W3C format.
 *
 * Declared as a top-level class so that {@code @SpringBootTest} component
 * scanning (which covers the main source set) can discover it via
 * {@code @Import} on the test class.
 */
@Component
public class HeaderCapturingConsumer {

    private final AtomicReference<String> capturedTraceparent = new AtomicReference<>();

    public void reset() {
        capturedTraceparent.set(null);
    }

    public String capturedTraceparent() {
        return capturedTraceparent.get();
    }

    @KafkaListener(
            topics = "trace-header-test",
            groupId = "trace-header-test-consumer"
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
