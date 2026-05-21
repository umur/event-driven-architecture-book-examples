package com.umurinan.eda.ch03;

import com.umurinan.eda.ch03.commands.UpdateWatchlistCommand;
import com.umurinan.eda.ch03.events.WatchlistUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class WatchlistService {

    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);
    static final String WATCHLIST_UPDATED_TOPIC = "watchlist-updated";

    private final KafkaTemplate<String, WatchlistUpdated> kafkaTemplate;

    public WatchlistService(KafkaTemplate<String, WatchlistUpdated> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public WatchlistResult updateProgress(UpdateWatchlistCommand command) {
        var updatedAt = Instant.now();
        var event = new WatchlistUpdated(
                command.movieId(),
                command.userId(),
                updatedAt,
                command.progressPercent()
        );

        try {
            var result = kafkaTemplate.send(WATCHLIST_UPDATED_TOPIC, command.userId(), event)
                    .get(5, TimeUnit.SECONDS);                                    // (1)
            log.info("Published WatchlistUpdated event for userId={} movieId={} to partition={} offset={}",
                    command.userId(),
                    command.movieId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (ExecutionException e) {
            throw new WatchlistProcessingException(                               // (2)
                    "Kafka send failed for userId=" + command.userId(), e.getCause());
        } catch (TimeoutException e) {
            throw new WatchlistProcessingException(                               // (3)
                    "Kafka unavailable, send timed out for userId=" + command.userId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WatchlistProcessingException(                               // (4)
                    "Send interrupted for userId=" + command.userId(), e);
        }

        return new WatchlistResult(command.movieId(), command.userId(), updatedAt);
    }
}
