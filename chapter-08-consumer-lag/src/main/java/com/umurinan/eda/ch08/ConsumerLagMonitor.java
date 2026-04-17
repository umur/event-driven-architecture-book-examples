package com.umurinan.eda.ch08;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes per-container Kafka consumer lag as Micrometer gauges.
 *
 * <p>Spring Kafka 4.x exposes raw Kafka client metrics through
 * {@link MessageListenerContainer#metrics()}, which returns
 * {@code Map<String, Map<MetricName, ? extends Metric>>} where the outer key
 * is the Kafka client-id. The metric we care about is {@code records-lag-max}:
 * the maximum number of records behind the latest offset across all assigned
 * partitions for this consumer.
 *
 * <p>Gauges are registered on {@link ApplicationStartedEvent} — after listener
 * containers have been started by the registry — rather than in {@code @PostConstruct},
 * which fires before the containers are running and would find an empty registry.
 *
 * <p>Because a Gauge reads its value lazily on every scrape, it always reflects
 * the current lag without any polling loop on our side.
 *
 * <p>Tags:
 * <ul>
 *   <li>{@code container.id} — the listener container id (e.g. "orders-0")</li>
 *   <li>{@code group.id}     — Kafka consumer group</li>
 * </ul>
 */
@Component
public class ConsumerLagMonitor {

    private static final Logger log = LoggerFactory.getLogger(ConsumerLagMonitor.class);

    static final String METRIC_NAME      = "kafka.consumer.lag.current";
    static final String LAG_METRIC_KEY   = "records-lag-max";
    static final String LAG_METRIC_GROUP = "consumer-fetch-manager-metrics";

    private final MeterRegistry meterRegistry;
    private final KafkaListenerEndpointRegistry endpointRegistry;

    public ConsumerLagMonitor(MeterRegistry meterRegistry,
                              KafkaListenerEndpointRegistry endpointRegistry) {
        this.meterRegistry    = meterRegistry;
        this.endpointRegistry = endpointRegistry;
    }

    /**
     * Registers one Micrometer gauge per listener container.
     *
     * <p>Triggered by {@link ApplicationStartedEvent}, which fires after
     * {@code KafkaListenerEndpointRegistry} has started all auto-started containers.
     * At this point {@code getAllListenerContainers()} returns the running containers
     * and the gauges can close over live container references.
     */
    @EventListener(ApplicationStartedEvent.class)
    public void registerLagGauges() {
        for (var container : endpointRegistry.getAllListenerContainers()) {
            var containerId = container.getListenerId();
            var groupId     = resolveGroupId(container);

            log.info("Registering lag gauge for container={} groupId={}", containerId, groupId);

            Gauge.builder(METRIC_NAME, container, this::readLag)
                    .description("Current maximum consumer lag (records-lag-max) across all assigned partitions")
                    .tag("container.id", containerId != null ? containerId : "unknown")
                    .tag("group.id",     groupId)
                    .register(meterRegistry);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Reads {@code records-lag-max} from the container's live Kafka metrics map.
     *
     * <p>{@code MessageListenerContainer.metrics()} returns
     * {@code Map<String, Map<MetricName, ? extends Metric>>} where the outer key
     * is the Kafka client-id. We iterate all client entries and look for the
     * {@code records-lag-max} metric in the {@code consumer-fetch-manager-metrics}
     * group. Returns {@code -1.0} when the metric is not yet available (before the
     * first poll completes).
     */
    private double readLag(MessageListenerContainer container) {
        var outerMap = container.metrics();
        if (outerMap == null || outerMap.isEmpty()) {
            return -1.0;
        }

        for (Map<MetricName, ? extends Metric> innerMap : outerMap.values()) {
            for (Map.Entry<MetricName, ? extends Metric> entry : innerMap.entrySet()) {
                MetricName metricName = entry.getKey();
                if (LAG_METRIC_KEY.equals(metricName.name())
                        && LAG_METRIC_GROUP.equals(metricName.group())) {
                    Metric metric = entry.getValue();
                    Object value  = metric.metricValue();
                    if (value instanceof Number n) {
                        return n.doubleValue();
                    }
                }
            }
        }

        return -1.0;
    }

    /**
     * Extracts the consumer group-id from the container's consumer properties.
     * Falls back to "unknown" when the property is absent.
     */
    private String resolveGroupId(MessageListenerContainer container) {
        try {
            var props      = container.getContainerProperties();
            var kafkaProps = props.getKafkaConsumerProperties();
            if (kafkaProps != null) {
                var gid = kafkaProps.getProperty("group.id");
                if (gid != null) return gid;
            }
        } catch (Exception e) {
            log.debug("Could not resolve group.id for container {}", container.getListenerId(), e);
        }
        return "unknown";
    }
}
