package com.umurinan.eda.ch16;

import com.umurinan.eda.ch16.events.ContentPublished;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(topics = {"content-published.tenant-test"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("TenantAwareProducer publishes and TenantAwareConsumer receives via tenant topic")
class TenantAwareIntegrationTest {

    @TestConfiguration
    static class TestCaptureListeners {

        @Bean
        ContentCapture contentCapture() {
            return new ContentCapture();
        }
    }

    static class ContentCapture {
        final AtomicReference<ContentPublished> received = new AtomicReference<>();

        @KafkaListener(topics = "content-published.tenant-test", groupId = "test-content-capture")
        void capture(ContentPublished event) {
            received.set(event); // (1)
        }
    }

    @Autowired
    private TenantAwareProducer producer;

    @Autowired
    private ContentCapture contentCapture;

    @BeforeEach
    void reset() {
        contentCapture.received.set(null);
    }

    @Test
    @DisplayName("event published for tenant-test is received on content-published.tenant-test")
    void eventReachesCorrectTenantTopic() {
        var event = new ContentPublished(
                "movie-42",
                "Dune Part Three",
                "tenant-test",
                Instant.now()
        );

        producer.publish(event);

        await()
                .atMost(15, SECONDS)
                .untilAsserted(() -> assertThat(contentCapture.received.get()).isNotNull());

        assertThat(contentCapture.received.get().contentId()).isEqualTo("movie-42");
        assertThat(contentCapture.received.get().tenantId()).isEqualTo("tenant-test");
    }
}
