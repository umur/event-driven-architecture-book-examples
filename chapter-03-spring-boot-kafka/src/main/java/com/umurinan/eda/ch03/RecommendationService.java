package com.umurinan.eda.ch03;

import com.umurinan.eda.ch03.events.WatchlistUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    @KafkaListener(topics = "watchlist-updated", groupId = "recommendation-service")
    public void onWatchlistUpdated(
            WatchlistUpdated event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            Acknowledgment ack) {

        log.info("Received WatchlistUpdated for userId={} movieId={} progress={}% from partition={}",
                event.userId(),
                event.movieId(),
                event.progressPercent(),
                partition);

        ack.acknowledge();
    }
}
