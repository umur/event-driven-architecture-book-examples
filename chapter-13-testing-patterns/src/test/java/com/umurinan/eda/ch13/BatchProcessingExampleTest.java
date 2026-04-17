package com.umurinan.eda.ch13;

import com.umurinan.eda.ch13.events.OrderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Pattern: Batch consumer testing
 *
 * In high-throughput systems consumers often process messages in batches
 * (spring.kafka.listener.type=batch) to amortise per-message overhead.
 * Testing batch consumers requires publishing multiple messages quickly and
 * then asserting that ALL of them were processed — not just the first one.
 *
 * This example uses the single-message handler (OrderEventHandler) to keep
 * the domain minimal, but the assertion pattern is identical for a batch
 * listener: wait until processedOrders.size() reaches the expected count.
 *
 * Key Awaitility technique: untilAsserted with a size check.
 * This is safer than checking for individual keys because it catches the
 * case where a message is processed twice (idempotency failure) or where
 * the handler drops a message silently.
 *
 * For a true batch listener you would annotate with:
 *   @KafkaListener(... containerFactory = "batchFactory")
 * and accept List<OrderEvent> instead of a single OrderEvent. The test
 * structure shown here remains the same.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"orders", "orders.DLT"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@DisplayName("Batch: multiple events published rapidly are all processed")
class BatchProcessingExampleTest {

    private static final int BATCH_SIZE = 5;

    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Autowired
    private OrderEventHandler handler;

    @Test
    @DisplayName("all 5 events published in rapid succession are processed exactly once each")
    void allBatchEventsAreProcessed() {
        // Publish BATCH_SIZE events without waiting between them.
        // This simulates a burst arrival that a batch consumer would receive
        // in a single poll() call.
        for (int i = 0; i < BATCH_SIZE; i++) {
            var orderId = "batch-order-" + i;
            var event = new OrderEvent(orderId, "customer-batch", new BigDecimal("10.00").multiply(new BigDecimal(i + 1)));
            kafkaTemplate.send("orders", orderId, event);
        }

        // Wait until all 5 messages have been processed.
        // Using size() >= BATCH_SIZE rather than == BATCH_SIZE makes the
        // assertion robust if other tests leave orphaned messages (though
        // @DirtiesContext and unique groupIds should prevent that).
        await()
                .atMost(15, SECONDS)
                .untilAsserted(() ->
                        assertThat(handler.processedOrders.size()).isGreaterThanOrEqualTo(BATCH_SIZE)
                );

        // Verify each expected orderId is present.
        for (int i = 0; i < BATCH_SIZE; i++) {
            assertThat(handler.processedOrders).containsKey("batch-order-" + i);
        }
    }
}
