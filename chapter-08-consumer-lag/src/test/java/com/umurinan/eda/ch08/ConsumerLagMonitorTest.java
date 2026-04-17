package com.umurinan.eda.ch08;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConsumerLagMonitor}.
 *
 * All tests run without a Spring context. The monitor's two responsibilities are
 * tested in isolation:
 *
 * <ol>
 *   <li>Gauge registration — one gauge per container with correct tags.</li>
 *   <li>Lag reading — the {@code readLag} method, exercised indirectly through
 *       the gauge's value supplier.</li>
 * </ol>
 *
 * Because {@code readLag} is private, we drive it through the public
 * {@link ConsumerLagMonitor#registerLagGauges()} entry-point and then inspect
 * the gauge value that Micrometer computes on demand.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConsumerLagMonitor")
class ConsumerLagMonitorTest {

    private SimpleMeterRegistry meterRegistry;

    @Mock
    private KafkaListenerEndpointRegistry endpointRegistry;

    private ConsumerLagMonitor monitor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        monitor = new ConsumerLagMonitor(meterRegistry, endpointRegistry);
    }

    // -------------------------------------------------------------------------
    // Gauge registration
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("registerLagGauges()")
    class RegisterLagGauges {

        @Test
        @DisplayName("registers one gauge when the registry has one container")
        void registersOneGaugeForOneContainer() {
            var container = containerWithId("orders-0", null);
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            var gauges = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME).gauges();
            assertThat(gauges).hasSize(1);
        }

        @Test
        @DisplayName("registers one gauge per container when the registry has multiple containers")
        void registersOneGaugePerContainer() {
            var c1 = containerWithId("orders-0", null);
            var c2 = containerWithId("orders-1", null);
            var c3 = containerWithId("orders-2", null);
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(c1, c2, c3));

            monitor.registerLagGauges();

            var gauges = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME).gauges();
            assertThat(gauges).hasSize(3);
        }

        @Test
        @DisplayName("registers no gauges when the registry has no containers")
        void registersNoGaugesForEmptyRegistry() {
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of());

            monitor.registerLagGauges();

            var gauges = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME).gauges();
            assertThat(gauges).isEmpty();
        }

        @Test
        @DisplayName("gauge carries the container.id tag from the listener container")
        void gaugeCarriesContainerIdTag() {
            var container = containerWithId("orders-0", null);
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            var gauge = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME)
                    .tag("container.id", "orders-0")
                    .gauge();
            assertThat(gauge).as("gauge with tag container.id=orders-0 should exist").isNotNull();
        }

        @Test
        @DisplayName("gauge carries group.id tag resolved from container consumer properties")
        void gaugeCarriesGroupIdTagWhenSet() {
            var props = new Properties();
            props.setProperty("group.id", "order-processor");
            var container = containerWithIdAndGroupProps("orders-0", props);
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            var gauge = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME)
                    .tag("group.id", "order-processor")
                    .gauge();
            assertThat(gauge).as("gauge with tag group.id=order-processor should exist").isNotNull();
        }

        @Test
        @DisplayName("gauge carries group.id=unknown when container has no group.id property")
        void gaugeCarriesUnknownGroupIdWhenNotSet() {
            var container = containerWithId("orders-0", null);
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            var gauge = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME)
                    .tag("group.id", "unknown")
                    .gauge();
            assertThat(gauge).as("gauge with tag group.id=unknown should exist").isNotNull();
        }

        @Test
        @DisplayName("gauge carries container.id=unknown when container returns null listener id")
        void gaugeCarriesUnknownContainerIdWhenListenerIdIsNull() {
            var container = containerWithId(null, null);
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            var gauge = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME)
                    .tag("container.id", "unknown")
                    .gauge();
            assertThat(gauge).as("gauge with tag container.id=unknown should exist").isNotNull();
        }
    }

    // -------------------------------------------------------------------------
    // Lag reading — driven through the gauge value supplier
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("readLag() (via gauge value)")
    class ReadLag {

        @Test
        @DisplayName("returns -1.0 when container.metrics() is null")
        void returnsMinusOneWhenMetricsMapIsNull() {
            var container = containerWithId("orders-0", null);
            when(container.metrics()).thenReturn(null);
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            assertGaugeValue("orders-0", -1.0);
        }

        @Test
        @DisplayName("returns -1.0 when container.metrics() is empty")
        void returnsMinusOneWhenMetricsMapIsEmpty() {
            var container = containerWithId("orders-0", null);
            when(container.metrics()).thenReturn(Collections.emptyMap());
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            assertGaugeValue("orders-0", -1.0);
        }

        @Test
        @DisplayName("returns -1.0 when the metric key matches but the group does not")
        void returnsMinusOneWhenGroupDoesNotMatch() {
            var container = containerWithMetric(
                    "orders-0",
                    ConsumerLagMonitor.LAG_METRIC_KEY,
                    "wrong-group",
                    42.0
            );
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            assertGaugeValue("orders-0", -1.0);
        }

        @Test
        @DisplayName("returns -1.0 when the group matches but the metric key does not")
        void returnsMinusOneWhenKeyDoesNotMatch() {
            var container = containerWithMetric(
                    "orders-0",
                    "wrong-metric-key",
                    ConsumerLagMonitor.LAG_METRIC_GROUP,
                    42.0
            );
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            assertGaugeValue("orders-0", -1.0);
        }

        @Test
        @DisplayName("returns the numeric value when records-lag-max is present with a Number value")
        void returnsLagWhenCorrectMetricIsPresent() {
            var container = containerWithMetric(
                    "orders-0",
                    ConsumerLagMonitor.LAG_METRIC_KEY,
                    ConsumerLagMonitor.LAG_METRIC_GROUP,
                    150.0
            );
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            assertGaugeValue("orders-0", 150.0);
        }

        @Test
        @DisplayName("returns 0.0 when records-lag-max is present and lag is zero")
        void returnsZeroWhenLagIsZero() {
            var container = containerWithMetric(
                    "orders-0",
                    ConsumerLagMonitor.LAG_METRIC_KEY,
                    ConsumerLagMonitor.LAG_METRIC_GROUP,
                    0.0
            );
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            assertGaugeValue("orders-0", 0.0);
        }

        @Test
        @DisplayName("returns -1.0 when metricValue() is not a Number (e.g. a String)")
        void returnsMinusOneWhenMetricValueIsNotANumber() {
            var container = containerWithNonNumericMetric("orders-0");
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(container));

            monitor.registerLagGauges();

            assertGaugeValue("orders-0", -1.0);
        }

        @Test
        @DisplayName("reads lag independently for each container")
        void readsLagIndependentlyPerContainer() {
            var c1 = containerWithMetric("orders-0", ConsumerLagMonitor.LAG_METRIC_KEY,
                    ConsumerLagMonitor.LAG_METRIC_GROUP, 10.0);
            var c2 = containerWithMetric("orders-1", ConsumerLagMonitor.LAG_METRIC_KEY,
                    ConsumerLagMonitor.LAG_METRIC_GROUP, 99.0);
            when(endpointRegistry.getAllListenerContainers()).thenReturn(List.of(c1, c2));

            monitor.registerLagGauges();

            assertGaugeValue("orders-0", 10.0);
            assertGaugeValue("orders-1", 99.0);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a mocked container with the given listener id and no Kafka consumer
     * properties (group.id will fall back to "unknown").
     */
    private MessageListenerContainer containerWithId(String listenerId, Properties kafkaProps) {
        var container = mock(MessageListenerContainer.class);
        var containerProps = mock(ContainerProperties.class);

        when(container.getListenerId()).thenReturn(listenerId);
        when(container.getContainerProperties()).thenReturn(containerProps);
        when(containerProps.getKafkaConsumerProperties()).thenReturn(kafkaProps);
        when(container.metrics()).thenReturn(Collections.emptyMap());

        return container;
    }

    /**
     * Creates a mocked container whose {@link ContainerProperties} returns the
     * given {@link Properties} instance carrying an explicit {@code group.id}.
     */
    private MessageListenerContainer containerWithIdAndGroupProps(String listenerId, Properties kafkaProps) {
        var container = mock(MessageListenerContainer.class);
        var containerProps = mock(ContainerProperties.class);

        when(container.getListenerId()).thenReturn(listenerId);
        when(container.getContainerProperties()).thenReturn(containerProps);
        when(containerProps.getKafkaConsumerProperties()).thenReturn(kafkaProps);
        when(container.metrics()).thenReturn(Collections.emptyMap());

        return container;
    }

    /**
     * Creates a mocked container that exposes a single Kafka metric with the
     * given name, group, and numeric value.
     */
    private MessageListenerContainer containerWithMetric(
            String listenerId, String metricKey, String metricGroup, double value) {

        var container = mock(MessageListenerContainer.class);
        var containerProps = mock(ContainerProperties.class);

        when(container.getListenerId()).thenReturn(listenerId);
        when(container.getContainerProperties()).thenReturn(containerProps);
        when(containerProps.getKafkaConsumerProperties()).thenReturn(null);

        MetricName metricName = new MetricName(metricKey, metricGroup, "", Map.of());
        Metric metric = mock(Metric.class);
        when(metric.metricValue()).thenReturn(value);

        Map<MetricName, Metric> innerMap = new HashMap<>();
        innerMap.put(metricName, metric);
        Map<String, Map<MetricName, ? extends Metric>> outerMap = Map.of("client-1", innerMap);

        when(container.metrics()).thenReturn(outerMap);

        return container;
    }

    /**
     * Creates a mocked container whose {@code records-lag-max} metric returns a
     * non-numeric value (a String), which should cause {@code readLag} to return
     * {@code -1.0}.
     */
    private MessageListenerContainer containerWithNonNumericMetric(String listenerId) {
        var container = mock(MessageListenerContainer.class);
        var containerProps = mock(ContainerProperties.class);

        when(container.getListenerId()).thenReturn(listenerId);
        when(container.getContainerProperties()).thenReturn(containerProps);
        when(containerProps.getKafkaConsumerProperties()).thenReturn(null);

        MetricName metricName = new MetricName(
                ConsumerLagMonitor.LAG_METRIC_KEY,
                ConsumerLagMonitor.LAG_METRIC_GROUP,
                "", Map.of());
        Metric metric = mock(Metric.class);
        when(metric.metricValue()).thenReturn("not-a-number");

        Map<MetricName, Metric> innerMap = new HashMap<>();
        innerMap.put(metricName, metric);
        Map<String, Map<MetricName, ? extends Metric>> outerMap = Map.of("client-1", innerMap);

        when(container.metrics()).thenReturn(outerMap);

        return container;
    }

    /**
     * Looks up the gauge tagged with the given {@code container.id} and asserts
     * its current value.
     */
    private void assertGaugeValue(String containerId, double expected) {
        Gauge gauge = meterRegistry.find(ConsumerLagMonitor.METRIC_NAME)
                .tag("container.id", containerId)
                .gauge();
        assertThat(gauge)
                .as("gauge for container.id=%s should be registered", containerId)
                .isNotNull();
        assertThat(gauge.value())
                .as("lag value for container.id=%s", containerId)
                .isEqualTo(expected);
    }
}
