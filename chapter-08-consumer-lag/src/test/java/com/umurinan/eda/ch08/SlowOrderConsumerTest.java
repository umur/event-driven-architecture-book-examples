package com.umurinan.eda.ch08;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link SlowOrderConsumer}.
 *
 * The consumer has two observable behaviours that are worth pinning down:
 *
 * <ol>
 *   <li>It always acknowledges the message after processing, regardless of the
 *       payload content. A missing {@code ack.acknowledge()} call would cause
 *       the partition to stall indefinitely.</li>
 *   <li>It restores the thread interrupt flag when {@link InterruptedException}
 *       is thrown during the sleep, so that callers higher up the call stack
 *       can detect and honour the interruption.</li>
 * </ol>
 *
 * The 200 ms artificial delay is not tested here — it is an intentional design
 * choice to demonstrate lag build-up, not application logic that needs
 * verification.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SlowOrderConsumer")
class SlowOrderConsumerTest {

    @Mock
    private Acknowledgment acknowledgment;

    private SlowOrderConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SlowOrderConsumer();
    }

    @Test
    @DisplayName("onOrder() acknowledges the message after processing the payload")
    void onOrder_acknowledgesMessage() {
        consumer.onOrder("order-payload-1", acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onOrder() acknowledges every message regardless of payload content")
    void onOrder_acknowledgesMessageForAnyPayload() {
        consumer.onOrder("", acknowledgment);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onOrder() restores the thread interrupt flag when interrupted during sleep")
    void onOrder_restoresInterruptFlagOnInterruption() throws Exception {
        // Interrupt the current thread before the consumer is invoked so that
        // Thread.sleep() throws InterruptedException immediately.
        Thread.currentThread().interrupt();

        try {
            consumer.onOrder("order-payload-interrupted", acknowledgment);

            assertThat(Thread.currentThread().isInterrupted())
                    .as("interrupt flag should be restored after InterruptedException")
                    .isTrue();

            // The consumer must still acknowledge even when interrupted.
            verify(acknowledgment).acknowledge();
        } finally {
            // Clear the interrupt flag so that it does not leak into subsequent tests.
            Thread.interrupted();
        }
    }
}
