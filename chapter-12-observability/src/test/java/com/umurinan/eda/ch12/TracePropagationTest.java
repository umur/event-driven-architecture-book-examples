package com.umurinan.eda.ch12;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies that the W3C traceparent context injected into Kafka headers by
 * micrometer-tracing-bridge-otel is extracted on the consumer side, so both
 * producer and consumer share the same trace ID.
 *
 * Spring Kafka 4.x requires {@code spring.kafka.template.observation-enabled=true}
 * for the KafkaTemplate to inject tracing headers, and
 * {@code spring.kafka.listener.observation-enabled=true} for the listener
 * container to restore the trace context. Both are set in
 * {@code src/test/resources/application.yml}.
 *
 * A dedicated topic {@code order-events-trace-test} avoids competing with the
 * application's own {@link PaymentEventHandler} which listens on
 * {@code order-events}.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"order-events-trace-test"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "management.tracing.sampling.probability=1.0",
        "spring.kafka.template.observation-enabled=true",
        "spring.kafka.listener.observation-enabled=true"
})
@Import({TracingTestConfig.class, TestTraceCapture.class})
@DirtiesContext
@EnabledIfSystemProperty(named = "test.tracing.enabled", matches = "true",
        disabledReason = "Requires full OTel tracing auto-configuration; run with -Dtest.tracing.enabled=true")
class TracePropagationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private Tracer tracer;

    @Autowired
    private TestTraceCapture traceCapture;

    @BeforeEach
    void resetCapture() {
        traceCapture.reset();
    }

    @Test
    @DisplayName("Consumer receives a traceparent header whose traceId matches the producer span")
    void consumerHeaderTraceIdMatchesProducerSpan() {
        var producerSpan = tracer.nextSpan().name("test.order.publish").start();
        String expectedTraceId;

        try (var ws = tracer.withSpan(producerSpan)) {
            expectedTraceId = producerSpan.context().traceId();
            kafkaTemplate.send("order-events-trace-test", "order-001",
                    "{\"orderId\":\"order-001\"}");
        } finally {
            producerSpan.end();
        }

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(traceCapture.capturedTraceparent()).isNotNull()
        );

        // traceparent format: 00-<32-hex traceId>-<16-hex spanId>-<flags>
        String traceparent = traceCapture.capturedTraceparent();
        String[] parts = traceparent.split("-");
        assertThat(parts).as("traceparent must have 4 dash-separated segments").hasSize(4);

        String consumedTraceId = parts[1];
        assertThat(consumedTraceId)
                .as("traceId in consumer traceparent header must match the producer span traceId")
                .isEqualTo(expectedTraceId);
    }
}
