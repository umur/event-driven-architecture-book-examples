package com.umurinan.eda.ch13;

import com.umurinan.eda.ch13.events.OrderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Pattern: Real Kafka with Testcontainers
 *
 * EmbeddedKafka is fast and requires no Docker, but it runs a ZooKeeper-free
 * in-process broker that differs from production Kafka in subtle ways
 * (partition leadership, log compaction, exactly-once semantics, etc.).
 *
 * Testcontainers solves this by pulling a real Apache Kafka image and
 * running it in Docker. Your test connects to a genuine Kafka broker, which
 * means:
 *   - Topic configurations behave exactly as in production.
 *   - Transactional producers work correctly.
 *   - Custom broker configs (compression, quotas) can be verified.
 *
 * Trade-off: the first run pulls ~600 MB and takes 10–30 seconds. Subsequent
 * runs reuse the cached image and start in ~3 seconds.
 *
 * When to choose Testcontainers over EmbeddedKafka:
 *   - You use Kafka transactions or exactly-once semantics.
 *   - You need to verify custom broker-side configuration.
 *   - You want your CI environment to mirror production as closely as possible.
 *   - You are testing a feature that has behaved differently on the embedded
 *     broker in the past.
 *
 * The @Container + static field pattern ensures one container instance is
 * shared across all test methods in this class, keeping startup cost low.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("Testcontainers: Integration test with real Kafka")
class TestcontainersExampleTest {

    @Container
    static KafkaContainer kafka =
            // Apache Kafka 4.x image (open source, matches production stack)
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.0"));

    /**
     * @DynamicPropertySource wires the container's randomly-assigned port into
     * the Spring application context before the context starts. This is the
     * Testcontainers equivalent of @TestPropertySource for dynamic values.
     */
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Autowired
    private OrderEventHandler handler;

    @Test
    @DisplayName("published OrderEvent is processed by the listener against a real Kafka broker")
    void publishedEventIsProcessedByRealKafka() {
        var orderId = UUID.randomUUID().toString();
        var event = new OrderEvent(orderId, "customer-tc", new BigDecimal("299.00"));

        kafkaTemplate.send("orders", orderId, event);

        await()
                .atMost(30, SECONDS)  // allow extra time for container cold-start in CI
                .untilAsserted(() ->
                        assertThat(handler.processedOrders).containsKey(orderId)
                );
    }
}
