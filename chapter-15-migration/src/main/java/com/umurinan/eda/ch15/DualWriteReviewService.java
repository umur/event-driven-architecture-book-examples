package com.umurinan.eda.ch15;

import com.umurinan.eda.ch15.domain.MovieReview;
import com.umurinan.eda.ch15.events.ReviewSubmitted;
import com.umurinan.eda.ch15.legacy.LegacyReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Primary
public class DualWriteReviewService {

    private static final Logger log = LoggerFactory.getLogger(DualWriteReviewService.class);
    static final String REVIEW_SUBMITTED_TOPIC = "review-submitted";

    private final LegacyReviewService legacyReviewService;
    private final KafkaTemplate<String, ReviewSubmitted> kafkaTemplate;

    public DualWriteReviewService(
            LegacyReviewService legacyReviewService,
            KafkaTemplate<String, ReviewSubmitted> kafkaTemplate) {
        this.legacyReviewService = legacyReviewService;
        this.kafkaTemplate = kafkaTemplate;
    }

    public MovieReview submitReview(String movieId, String userId, int rating) {
        var review = legacyReviewService.submitReview(movieId, userId, rating); // (1)

        var event = new ReviewSubmitted(
                review.reviewId(),
                review.movieId(),
                review.userId(),
                review.rating(),
                review.reviewedAt()
        );

        try {
            var result = kafkaTemplate.send(REVIEW_SUBMITTED_TOPIC, review.movieId(), event)
                    .get(5, TimeUnit.SECONDS); // (2)
            log.info("Published ReviewSubmitted event for reviewId={} to partition={} offset={}",
                    review.reviewId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (ExecutionException e) {
            throw new ReviewPublishException( // (3)
                    "Kafka send failed for reviewId=" + review.reviewId(), e.getCause());
        } catch (TimeoutException e) {
            throw new ReviewPublishException( // (4)
                    "Kafka send timed out for reviewId=" + review.reviewId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReviewPublishException( // (5)
                    "Send interrupted for reviewId=" + review.reviewId(), e);
        }

        return review;
    }
}
