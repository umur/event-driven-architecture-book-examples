package com.umurinan.eda.ch15;

import com.umurinan.eda.ch15.events.ReviewSubmitted;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"review-submitted"}, adminTimeout = 30)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DirtiesContext
@DisplayName("DualWriteReviewService integration")
class DualWriteIntegrationTest {

    @Autowired
    private DualWriteReviewService dualWriteReviewService;

    @Autowired
    private TestReviewSubmittedConsumer testConsumer;

    @Test
    @DisplayName("submitReview() publishes a ReviewSubmitted event that lands on the topic")
    void submitReview_eventLandsOnTopic() {
        dualWriteReviewService.submitReview("movie-int-1", "user-int-1", 5); // (1)

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(testConsumer.latch.await(0, TimeUnit.MILLISECONDS)).isTrue()
                ); // (2)
    }

    @TestConfiguration
    static class ConsumerConfig {
        @Bean
        TestReviewSubmittedConsumer testReviewSubmittedConsumer() {
            return new TestReviewSubmittedConsumer();
        }
    }

    static class TestReviewSubmittedConsumer {

        final CountDownLatch latch = new CountDownLatch(1);

        @KafkaListener(
                topics = "review-submitted",
                groupId = "test-review-observer",
                containerFactory = "kafkaListenerContainerFactory"
        )
        void observe(ReviewSubmitted event, Acknowledgment ack) {
            if (event.reviewId() != null) {
                latch.countDown(); // (3)
            }
            ack.acknowledge();
        }
    }
}
