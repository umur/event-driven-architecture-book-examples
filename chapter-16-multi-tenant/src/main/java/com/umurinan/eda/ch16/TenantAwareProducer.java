package com.umurinan.eda.ch16;

import com.umurinan.eda.ch16.events.ContentPublished;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class TenantAwareProducer {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareProducer.class);

    private final KafkaTemplate<String, ContentPublished> kafkaTemplate;

    public TenantAwareProducer(KafkaTemplate<String, ContentPublished> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ContentPublished event) {
        String topic = "content-published." + event.tenantId(); // (1)
        var record = new ProducerRecord<>(topic, event.contentId(), event); // (2)
        record.headers().add("X-Tenant-ID", event.tenantId().getBytes(StandardCharsets.UTF_8)); // (3)
        try {
            kafkaTemplate.send(record).get(5, TimeUnit.SECONDS); // (4)
            log.info("Published content {} to topic {}", event.contentId(), topic);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to publish event for tenant " + event.tenantId(), e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out publishing event for tenant " + event.tenantId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while publishing event for tenant " + event.tenantId(), e);
        }
    }
}
