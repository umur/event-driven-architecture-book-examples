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
 * Verifies that a W3C-compliant {@code traceparent} header is physically
 * present in the Kafka record headers when a message is sent while a
 * Micrometer tracing span is active.
 *
 * Format: {@code 00-<32 hex traceId>-<16 hex spanId>-<2 hex flags>}
 *
 * Spring Kafka 4.x requires {@code spring.kafka.template.observation-enabled=true}
 * for the KafkaTemplate to inject tracing headers automatically. This is set
 * in {@code src/test/resources/application.yml} and reinforced via
 * {@code @TestPropertySource}.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"trace-header-test"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "management.tracing.sampling.probability=1.0",
        "spring.kafka.template.observation-enabled=true",
        "spring.kafka.listener.observation-enabled=true"
})
@Import({TracingTestConfig.class, HeaderCapturingConsumer.class})
@DirtiesContext
@EnabledIfSystemProperty(named = "test.tracing.enabled", matches = "true",
        disabledReason = "Requires full OTel tracing auto-configuration; run with -Dtest.tracing.enabled=true")
class TraceHeaderTest {

    private static final String TRACEPARENT_PATTERN =
            "00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private Tracer tracer;

    @Autowired
    private HeaderCapturingConsumer headerConsumer;

    @BeforeEach
    void resetCapture() {
        headerConsumer.reset();
    }

    @Test
    @DisplayName("Kafka record contains a W3C traceparent header when sent inside an active span")
    void kafkaRecordContainsTraceparentHeader() {
        var span = tracer.nextSpan().name("test.header.check").start();

        try (var ws = tracer.withSpan(span)) {
            kafkaTemplate.send("trace-header-test", "hdr-key", "{\"test\":true}");
        } finally {
            span.end();
        }

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(headerConsumer.capturedTraceparent()).isNotNull()
        );

        String traceparent = headerConsumer.capturedTraceparent();
        assertThat(traceparent)
                .as("traceparent must follow W3C format: 00-<traceId>-<spanId>-<flags>")
                .matches(TRACEPARENT_PATTERN);
    }
}
