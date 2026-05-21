package com.umurinan.eda.ch16;

import com.umurinan.eda.ch16.events.ContentPublished;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class TenantAwareConsumer {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareConsumer.class);

    @KafkaListener(topicPattern = "content-published\\..*", groupId = "content-indexer") // (1)
    public void onContentPublished(
            ContentPublished event,
            @Header("X-Tenant-ID") byte[] tenantIdBytes,
            Acknowledgment ack) {
        String tenantId = new String(tenantIdBytes, StandardCharsets.UTF_8); // (2)
        TenantContext.set(tenantId); // (3)
        try {
            log.info("Indexing content {} for tenant {}", event.contentId(), tenantId);
        } finally {
            TenantContext.clear(); // (4)
            ack.acknowledge(); // (5)
        }
    }
}
