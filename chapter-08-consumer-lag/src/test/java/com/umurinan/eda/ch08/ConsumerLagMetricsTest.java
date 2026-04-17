package com.umurinan.eda.ch08;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies that the consumer lag gauge is registered in the Micrometer registry
 * after messages have been published to the "orders" topic.
 *
 * The test only asserts that the gauge *exists* — not its current numeric value.
 * Lag magnitude depends on scheduling timing, which makes it non-deterministic in
 * a test environment. What matters for this chapter is that the metric is wired up
 * and visible to a scraper such as Prometheus.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {"orders"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("ConsumerLagMonitor integration")
class ConsumerLagMetricsTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("kafka.consumer.lag.current gauge is registered after messages are published")
    void lagGauge_isRegisteredAfterMessagesPublished() throws InterruptedException {
        // Send 20 messages rapidly. The consumer sleeps 200 ms per message so
        // it cannot keep up — lag will build, but the test only checks registration.
        for (int i = 0; i < 20; i++) {
            kafkaTemplate.send("orders", "key-" + i, "order-payload-" + i);
        }

        // Give the consumer a moment to start polling and for @PostConstruct to
        // have registered gauges (it runs at startup, so they should already be
        // present, but we wait briefly to avoid a race with container startup).
        Thread.sleep(1_000);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var gauge = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME).gauge();
                    assertThat(gauge)
                            .as("Gauge '%s' should be registered in the MeterRegistry",
                                    ConsumerLagMonitor.METRIC_NAME)
                            .isNotNull();
                });
    }
}
