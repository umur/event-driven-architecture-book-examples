package com.umurinan.eda.ch15.legacy;

import com.umurinan.eda.ch15.domain.MovieReview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class LegacyReviewService {

    private static final Logger log = LoggerFactory.getLogger(LegacyReviewService.class);

    private final ReviewRepository reviewRepository;

    public LegacyReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public MovieReview submitReview(String movieId, String userId, int rating) {
        var review = new MovieReview(
                UUID.randomUUID().toString(), // (1)
                movieId,
                userId,
                rating,
                Instant.now()
        );
        reviewRepository.save(review); // (2)
        log.info("Saved review reviewId={} movieId={} userId={}", review.reviewId(), movieId, userId);
        return review;
    }
}
