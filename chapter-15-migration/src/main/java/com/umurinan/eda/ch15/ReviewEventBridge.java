package com.umurinan.eda.ch15;

import com.umurinan.eda.ch15.events.ReviewSubmitted;
import com.umurinan.eda.ch15.legacy.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ReviewEventBridge {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventBridge.class);

    private final ReviewRepository reviewRepository;
    private final KafkaTemplate<String, ReviewSubmitted> kafkaTemplate;

    public ReviewEventBridge(
            ReviewRepository reviewRepository,
            KafkaTemplate<String, ReviewSubmitted> kafkaTemplate) {
        this.reviewRepository = reviewRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishExistingReviews() {
        var reviews = reviewRepository.findAll(); // (1)
        log.info("Publishing {} existing reviews to Kafka", reviews.size());

        for (var review : reviews) {
            var event = new ReviewSubmitted(
                    review.reviewId(),
                    review.movieId(),
                    review.userId(),
                    review.rating(),
                    review.reviewedAt()
            );

            try {
                kafkaTemplate.send(DualWriteReviewService.REVIEW_SUBMITTED_TOPIC, review.movieId(), event)
                        .get(5, TimeUnit.SECONDS); // (2)
                log.info("Backfilled ReviewSubmitted for reviewId={}", review.reviewId());
            } catch (ExecutionException e) {
                log.error("Kafka send failed for reviewId={}", review.reviewId(), e.getCause());
            } catch (TimeoutException e) {
                log.error("Kafka send timed out for reviewId={}", review.reviewId(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Send interrupted for reviewId={}", review.reviewId(), e);
                break; // (3)
            }
        }
    }
}
