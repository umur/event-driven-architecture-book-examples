package com.umurinan.eda.ch08;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Intentionally slow order consumer.
 *
 * The 200 ms sleep per message means the consumer can process at most 5
 * messages per second. When messages arrive faster than that, lag accumulates.
 * That is exactly the condition this chapter wants to make visible through
 * Micrometer metrics.
 */
@Service
public class SlowOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SlowOrderConsumer.class);

    @KafkaListener(topics = "orders", groupId = "order-processor")
    public void onOrder(String payload, Acknowledgment ack) {
        log.info("Processing: {}", payload);

        try {
            // Artificial slow-down — this is the whole point of the example.
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ack.acknowledge();
    }
}
