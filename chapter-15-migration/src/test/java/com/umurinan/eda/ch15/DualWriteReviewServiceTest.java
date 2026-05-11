package com.umurinan.eda.ch15;

import com.umurinan.eda.ch15.domain.MovieReview;
import com.umurinan.eda.ch15.events.ReviewSubmitted;
import com.umurinan.eda.ch15.legacy.LegacyReviewService;
import com.umurinan.eda.ch15.legacy.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DualWriteReviewService")
class DualWriteReviewServiceTest {

    @Mock
    private KafkaTemplate<String, ReviewSubmitted> kafkaTemplate;

    @Mock
    private ReviewRepository reviewRepository;

    private DualWriteReviewService dualWriteReviewService;

    @BeforeEach
    void setUp() {
        var legacyService = new LegacyReviewService(reviewRepository);
        dualWriteReviewService = new DualWriteReviewService(legacyService, kafkaTemplate);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(ReviewSubmitted.class)))
                .thenReturn(CompletableFuture.completedFuture(null)); // (1)
    }

    @Test
    @DisplayName("submitReview() saves the review to the repository")
    void submitReview_savesToRepository() {
        dualWriteReviewService.submitReview("movie-1", "user-1", 5);

        verify(reviewRepository).save(any(MovieReview.class)); // (2)
    }

    @Test
    @DisplayName("submitReview() publishes a ReviewSubmitted event to Kafka")
    void submitReview_publishesEvent() {
        dualWriteReviewService.submitReview("movie-1", "user-1", 5);

        verify(kafkaTemplate).send(eq("review-submitted"), any(String.class), any(ReviewSubmitted.class)); // (3)
    }

    @Test
    @DisplayName("submitReview() uses the movieId as the Kafka message key")
    void submitReview_usesMovieIdAsMessageKey() {
        dualWriteReviewService.submitReview("movie-42", "user-7", 4);

        verify(kafkaTemplate).send(eq("review-submitted"), eq("movie-42"), any(ReviewSubmitted.class));
    }

    @Test
    @DisplayName("submitReview() event carries the same rating as the saved review")
    void submitReview_eventCarriesCorrectRating() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ReviewSubmitted> eventCaptor = ArgumentCaptor.forClass(ReviewSubmitted.class);

        dualWriteReviewService.submitReview("movie-5", "user-3", 3);

        verify(kafkaTemplate).send(eq("review-submitted"), any(String.class), eventCaptor.capture());
        assertThat(eventCaptor.getValue().rating()).isEqualTo(3);
    }

    @Test
    @DisplayName("submitReview() returns the MovieReview from the legacy service")
    void submitReview_returnsMovieReview() {
        var result = dualWriteReviewService.submitReview("movie-9", "user-2", 4);

        assertThat(result).isNotNull();
        assertThat(result.movieId()).isEqualTo("movie-9");
        assertThat(result.userId()).isEqualTo("user-2");
        assertThat(result.rating()).isEqualTo(4);
    }
}
