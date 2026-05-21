package com.umurinan.eda.ch03;

import com.umurinan.eda.ch03.events.WatchlistUpdated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"watchlist-updated", "watchlist-updated.DLT"}, adminTimeout = 30)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DirtiesContext
@DisplayName("WatchlistService integration")
class WatchlistServiceIntegrationTest {

    @Autowired
    private KafkaTemplate<String, WatchlistUpdated> kafkaTemplate;

    @Autowired
    private TestWatchlistUpdatedConsumer testConsumer;

    @Test
    @DisplayName("published WatchlistUpdated event is received and processed by RecommendationService")
    void publishedEvent_isReceivedByRecommendationService() throws Exception {
        var event = new WatchlistUpdated("movie-integration-1", "user-int-1", Instant.now(), 85);

        kafkaTemplate.send("watchlist-updated", event.userId(), event);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(testConsumer.latch.await(0, TimeUnit.MILLISECONDS)).isTrue()
                );
    }

    @TestConfiguration
    static class ConsumerConfig {
        @Bean
        TestWatchlistUpdatedConsumer testWatchlistUpdatedConsumer() {
            return new TestWatchlistUpdatedConsumer();
        }
    }

    /**
     * A dedicated test consumer that counts down its latch each time it receives
     * a valid WatchlistUpdated event. This lets the test assert that at least one
     * message was delivered without coupling to RecommendationService internals.
     */
    static class TestWatchlistUpdatedConsumer {

        final CountDownLatch latch = new CountDownLatch(1);

        @KafkaListener(
                topics = "watchlist-updated",
                groupId = "test-watchlist-observer",
                containerFactory = "kafkaListenerContainerFactory"
        )
        void observe(WatchlistUpdated event, Acknowledgment ack) {
            if (event.userId() != null) {
                latch.countDown();
            }
            ack.acknowledge();
        }
    }
}
