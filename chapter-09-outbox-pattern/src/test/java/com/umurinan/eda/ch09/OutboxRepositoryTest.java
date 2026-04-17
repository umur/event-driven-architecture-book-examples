package com.umurinan.eda.ch09;

import com.umurinan.eda.ch09.outbox.OutboxMessage;
import com.umurinan.eda.ch09.outbox.OutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("OutboxRepository")
class OutboxRepositoryTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    @DisplayName("findByPublishedAtIsNullOrderByCreatedAtAsc() returns only unpublished messages")
    void returnsOnlyUnpublishedMessages() {
        var unpublished = new OutboxMessage("Order", "order-1", "OrderPlaced", "{\"orderId\":\"order-1\"}");
        var published = new OutboxMessage("Order", "order-2", "OrderPlaced", "{\"orderId\":\"order-2\"}");
        published.setPublishedAt(Instant.now());

        outboxRepository.save(unpublished);
        outboxRepository.save(published);

        var result = outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAggregateId()).isEqualTo("order-1");
    }

    @Test
    @DisplayName("findByPublishedAtIsNullOrderByCreatedAtAsc() returns messages in createdAt ascending order")
    void returnsMessagesInCreatedAtAscendingOrder() throws InterruptedException {
        var first = new OutboxMessage("Order", "order-a", "OrderPlaced", "{\"orderId\":\"order-a\"}");
        outboxRepository.saveAndFlush(first);

        // Small delay to guarantee distinct createdAt values from Instant.now() in the constructor
        Thread.sleep(5);
        var second = new OutboxMessage("Order", "order-b", "OrderPlaced", "{\"orderId\":\"order-b\"}");
        outboxRepository.saveAndFlush(second);

        Thread.sleep(5);
        var third = new OutboxMessage("Order", "order-c", "OrderPlaced", "{\"orderId\":\"order-c\"}");
        outboxRepository.saveAndFlush(third);

        var result = outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();

        assertThat(result).extracting(OutboxMessage::getAggregateId)
                .containsExactly("order-a", "order-b", "order-c");
    }

    @Test
    @DisplayName("after setting publishedAt and saving, message no longer appears in unpublished query")
    void markedAsPublished_doesNotAppearInUnpublishedQuery() {
        var message = new OutboxMessage("Order", "order-x", "OrderPlaced", "{\"orderId\":\"order-x\"}");
        outboxRepository.saveAndFlush(message);

        assertThat(outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc()).hasSize(1);

        message.setPublishedAt(Instant.now());
        outboxRepository.saveAndFlush(message);

        assertThat(outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc()).isEmpty();
    }
}
