package com.umurinan.eda.ch03;

import com.umurinan.eda.ch03.commands.UpdateWatchlistCommand;
import com.umurinan.eda.ch03.events.WatchlistUpdated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WatchlistService")
class WatchlistServiceTest {

    @Mock
    private KafkaTemplate<String, WatchlistUpdated> kafkaTemplate;

    private WatchlistService watchlistService;

    @BeforeEach
    void setUp() {
        watchlistService = new WatchlistService(kafkaTemplate);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(WatchlistUpdated.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("updateProgress() sends the event to the 'watchlist-updated' topic")
    void updateProgress_sendsToWatchlistUpdatedTopic() {
        var command = new UpdateWatchlistCommand("movie-1", "user-1", 75);

        watchlistService.updateProgress(command);

        verify(kafkaTemplate).send(eq("watchlist-updated"), any(String.class), any(WatchlistUpdated.class));
    }

    @Test
    @DisplayName("updateProgress() uses the userId as the Kafka message key")
    void updateProgress_usesUserIdAsMessageKey() {
        var command = new UpdateWatchlistCommand("movie-42", "user-7", 50);

        watchlistService.updateProgress(command);

        verify(kafkaTemplate).send(eq("watchlist-updated"), eq("user-7"), any(WatchlistUpdated.class));
    }

    @Test
    @DisplayName("updateProgress() returns a WatchlistResult with the correct movieId")
    void updateProgress_returnsResultWithCorrectMovieId() {
        var command = new UpdateWatchlistCommand("movie-7", "user-3", 100);

        var result = watchlistService.updateProgress(command);

        assertThat(result.movieId()).isEqualTo("movie-7");
    }

    @Test
    @DisplayName("updateProgress() sets a non-null updatedAt timestamp on the result")
    void updateProgress_setsNonNullUpdatedAt() {
        var command = new UpdateWatchlistCommand("movie-99", "user-5", 30);

        var result = watchlistService.updateProgress(command);

        assertThat(result.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateProgress() embeds the same updatedAt timestamp in the published event")
    void updateProgress_eventCarriesSameUpdatedAtAsResult() {
        var command = new UpdateWatchlistCommand("movie-55", "user-9", 60);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<WatchlistUpdated> eventCaptor = ArgumentCaptor.forClass(WatchlistUpdated.class);

        var result = watchlistService.updateProgress(command);

        verify(kafkaTemplate).send(eq("watchlist-updated"), eq("user-9"), eventCaptor.capture());

        assertThat(eventCaptor.getValue().watchedAt()).isEqualTo(result.updatedAt());
    }
}
