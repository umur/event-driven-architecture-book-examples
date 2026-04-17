package com.umurinan.eda.ch08;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test that verifies the gauge registered by {@link ConsumerLagMonitor}
 * carries the expected tag values once the Spring context and embedded Kafka are up.
 *
 * The existing {@link ConsumerLagMetricsTest} checks that the gauge *exists* after
 * messages are published. This test focuses on the structural correctness of the
 * metric: the tag names and values must match what a Prometheus scraper or an
 * alerting rule would reference.
 *
 * No messages need to be published here — the gauge is registered on
 * {@code ApplicationStartedEvent}, which fires as part of context startup, so the
 * metric is present as soon as the context is ready.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"orders"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("ConsumerLagMonitor gauge tags")
class ConsumerLagGaugeTagsTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("gauge is registered with a non-blank container.id tag")
    void gauge_hasNonBlankContainerIdTag() {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var gauge = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME).gauge();
            assertThat(gauge)
                    .as("gauge '%s' must be registered", ConsumerLagMonitor.METRIC_NAME)
                    .isNotNull();

            String containerId = gauge.getId().getTag("container.id");
            assertThat(containerId)
                    .as("container.id tag must be present and non-blank")
                    .isNotBlank();
        });
    }

    @Test
    @DisplayName("gauge is registered with a non-blank group.id tag")
    void gauge_hasNonBlankGroupIdTag() {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var gauge = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME).gauge();
            assertThat(gauge)
                    .as("gauge '%s' must be registered", ConsumerLagMonitor.METRIC_NAME)
                    .isNotNull();

            String groupId = gauge.getId().getTag("group.id");
            assertThat(groupId)
                    .as("group.id tag must be present and non-blank")
                    .isNotBlank();
        });
    }

    @Test
    @DisplayName("gauge initial value is -1.0 or a non-negative number (never NaN)")
    void gauge_initialValueIsValidNumber() {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Gauge gauge = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME).gauge();
            assertThat(gauge)
                    .as("gauge '%s' must be registered", ConsumerLagMonitor.METRIC_NAME)
                    .isNotNull();

            double value = gauge.value();
            assertThat(Double.isNaN(value))
                    .as("gauge value must not be NaN, was %s", value)
                    .isFalse();
            // Before the first poll completes, readLag returns -1.0.
            // Once Kafka metrics are populated, the value is >= 0.
            assertThat(value)
                    .as("gauge value must be -1.0 (not yet available) or >= 0 (live lag)")
                    .satisfiesAnyOf(
                            v -> assertThat(v).isEqualTo(-1.0),
                            v -> assertThat(v).isGreaterThanOrEqualTo(0.0)
                    );
        });
    }
}
