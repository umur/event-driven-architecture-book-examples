package com.umurinan.eda.ch16;

import com.umurinan.eda.ch16.events.ContentPublished;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAwareProducerTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, ContentPublished> kafkaTemplate = mock(KafkaTemplate.class);

    private TenantAwareProducer producer;

    @BeforeEach
    void setUp() {
        producer = new TenantAwareProducer(kafkaTemplate);
    }

    @Test
    @DisplayName("publish() sends to topic content-published.<tenantId>")
    void publishRoutesToTenantTopic() {
        var event = new ContentPublished("content-001", "Interstellar", "tenant-abc", Instant.now());

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, ContentPublished>> future =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        producer.publish(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, ContentPublished>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        assertThat(captor.getValue().topic()).isEqualTo("content-published.tenant-abc"); // (1)
        assertThat(captor.getValue().key()).isEqualTo("content-001");
    }
}
