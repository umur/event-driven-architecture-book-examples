package com.umurinan.eda.ch09.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPoller(OutboxRepository outboxRepository,
                        KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 500)
    @Async("outboxExecutor")                                                      // (1)
    @Transactional
    public void poll() {
        var pending = outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();

        for (var message : pending) {
            var topic = topicFor(message.getEventType());
            try {
                kafkaTemplate.send(topic, message.getAggregateId(), message.getPayload())
                        .get(5, TimeUnit.SECONDS);                                // (2)
                message.setPublishedAt(Instant.now());                            // (3)
                outboxRepository.save(message);
                log.info("Published outbox message id={} type={} topic={}",
                        message.getId(), message.getEventType(), topic);
            } catch (TimeoutException e) {
                log.error("Kafka send timed out for outbox message id={} type={} topic={}",
                        message.getId(), message.getEventType(), topic);
                // (4) do not mark published — retry on next poll
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while publishing outbox message id={}", message.getId());
                break;                                                             // (5)
            } catch (ExecutionException e) {
                log.error("Failed to publish outbox message id={} type={} topic={}: {}",
                        message.getId(), message.getEventType(), topic, e.getCause().getMessage());
                // (6) continue to next message — one failure does not block the rest
            }
        }
    }

    /**
     * Derives the Kafka topic name from an event type string.
     * "OrderPlaced" -> "order-placed", "PaymentProcessed" -> "payment-processed"
     */
    static String topicFor(String eventType) {
        // Insert a hyphen before each uppercase letter that follows a lowercase letter,
        // then lowercase the whole string.
        return eventType
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .toLowerCase();
    }
}
