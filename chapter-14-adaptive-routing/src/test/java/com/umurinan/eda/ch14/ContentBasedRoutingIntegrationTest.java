package com.umurinan.eda.ch14;

import com.umurinan.eda.ch14.events.OrderPlaced;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.stereotype.Component;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for content-based routing end-to-end.
 *
 * Strategy:
 *   1. Publish three orders to orders-raw — one per tier.
 *   2. OrderRouter consumes orders-raw and forwards each order to the
 *      correct destination topic based on total.
 *   3. Three listener beans defined in TestCaptureListeners (a static
 *      @TestConfiguration inner class) capture routed messages into
 *      AtomicReferences that are injected into the test via @Autowired.
 *   4. Awaitility waits until all three references are populated, then we
 *      assert each destination received the right order.
 *
 * Why a static @TestConfiguration with @Bean methods returning @Component
 * instances rather than @KafkaListener directly on the test class?
 *
 * Spring processes @KafkaListener only on beans it manages. The test class
 * itself is not a Spring bean — it is instantiated by JUnit. A
 * @TestConfiguration inner class IS scanned as a configuration source by
 * @SpringBootTest, so beans declared there are fully managed and their
 * @KafkaListener annotations are processed normally.
 *
 * The @TestConfiguration must be static so Spring can instantiate it without
 * an enclosing instance.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"orders-raw", "orders-standard", "orders-premium", "orders-high-value"}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("Content-based routing: orders are forwarded to the correct topic")
class ContentBasedRoutingIntegrationTest {

    // ---------------------------------------------------------------------------
    // Test-only capture listeners — each listens to one destination topic and
    // stores the received message so the test method can assert against it.
    //
    // Declared as top-level static beans (not inner instances) so that Spring
    // Kafka's @KafkaListener post-processor can register the containers.
    // ---------------------------------------------------------------------------

    @TestConfiguration
    static class TestCaptureListeners {

        @Bean
        StandardCapture standardCapture() {
            return new StandardCapture();
        }

        @Bean
        PremiumCapture premiumCapture() {
            return new PremiumCapture();
        }

        @Bean
        HighValueCapture highValueCapture() {
            return new HighValueCapture();
        }
    }

    static class StandardCapture {
        final AtomicReference<OrderPlaced> received = new AtomicReference<>();

        @KafkaListener(topics = "orders-standard", groupId = "test-standard-capture")
        void capture(OrderPlaced order) {
            received.set(order);
        }
    }

    static class PremiumCapture {
        final AtomicReference<OrderPlaced> received = new AtomicReference<>();

        @KafkaListener(topics = "orders-premium", groupId = "test-premium-capture")
        void capture(OrderPlaced order) {
            received.set(order);
        }
    }

    static class HighValueCapture {
        final AtomicReference<OrderPlaced> received = new AtomicReference<>();

        @KafkaListener(topics = "orders-high-value", groupId = "test-high-value-capture")
        void capture(OrderPlaced order) {
            received.set(order);
        }
    }

    // ---------------------------------------------------------------------------
    // Test wiring
    // ---------------------------------------------------------------------------

    @Autowired
    private KafkaTemplate<String, OrderPlaced> kafkaTemplate;

    @Autowired
    private StandardCapture standardCapture;

    @Autowired
    private PremiumCapture premiumCapture;

    @Autowired
    private HighValueCapture highValueCapture;

    @BeforeEach
    void resetCaptures() {
        standardCapture.received.set(null);
        premiumCapture.received.set(null);
        highValueCapture.received.set(null);
    }

    // ---------------------------------------------------------------------------
    // Test
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("standard, premium, and high-value orders each land on the correct topic")
    void threeOrdersRouteToThreeTopics() {
        var standardOrder  = new OrderPlaced("order-std",  "customer-1", new BigDecimal("50.00"));
        var premiumOrder   = new OrderPlaced("order-prem", "customer-2", new BigDecimal("250.00"));
        var highValueOrder = new OrderPlaced("order-hv",   "customer-3", new BigDecimal("1500.00"));

        kafkaTemplate.send("orders-raw", standardOrder.orderId(),  standardOrder);
        kafkaTemplate.send("orders-raw", premiumOrder.orderId(),   premiumOrder);
        kafkaTemplate.send("orders-raw", highValueOrder.orderId(), highValueOrder);

        // Wait until all three destination listeners have captured a message.
        await()
                .atMost(15, SECONDS)
                .untilAsserted(() -> {
                    assertThat(standardCapture.received.get()).isNotNull();
                    assertThat(premiumCapture.received.get()).isNotNull();
                    assertThat(highValueCapture.received.get()).isNotNull();
                });

        // Assert each destination received the correct order, not just any order.
        assertThat(standardCapture.received.get().orderId()).isEqualTo("order-std");
        assertThat(premiumCapture.received.get().orderId()).isEqualTo("order-prem");
        assertThat(highValueCapture.received.get().orderId()).isEqualTo("order-hv");

        // Sanity-check that totals were not corrupted in transit.
        assertThat(standardCapture.received.get().total()).isEqualByComparingTo("50.00");
        assertThat(premiumCapture.received.get().total()).isEqualByComparingTo("250.00");
        assertThat(highValueCapture.received.get().total()).isEqualByComparingTo("1500.00");
    }
}
