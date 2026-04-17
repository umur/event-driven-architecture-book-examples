package com.umurinan.eda.ch12;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentEventHandler}.
 *
 * Verifies acknowledgment behavior and defensive handling of a missing trace
 * context, without requiring a Kafka broker or Spring application context.
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventHandlerTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span currentSpan;

    @Mock
    private TraceContext traceContext;

    @Mock
    private Acknowledgment acknowledgment;

    private PaymentEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PaymentEventHandler(tracer);
    }

    // ------------------------------------------------------------------
    // Helper: build a minimal ConsumerRecord without Kafka serialization
    // ------------------------------------------------------------------
    private ConsumerRecord<String, String> record(String key, String value) {
        return new ConsumerRecord<>(
                "order-events", 0, 0L,
                ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
                0, 0,
                key, value,
                new RecordHeaders(), Optional.empty());
    }

    @Nested
    @DisplayName("onOrderEvent — acknowledgment")
    class AcknowledgmentBehavior {

        @Test
        @DisplayName("acknowledges every message when a current span exists")
        void acknowledgesWhenSpanPresent() {
            when(tracer.currentSpan()).thenReturn(currentSpan);
            when(currentSpan.context()).thenReturn(traceContext);
            when(traceContext.traceId()).thenReturn("aabbccdd11223344aabbccdd11223344");

            handler.onOrderEvent(record("order-1", "{\"orderId\":\"order-1\"}"), acknowledgment);

            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("acknowledges even when no current span is active")
        void acknowledgesWhenNoSpan() {
            when(tracer.currentSpan()).thenReturn(null);

            handler.onOrderEvent(record("order-2", "{\"orderId\":\"order-2\"}"), acknowledgment);

            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("calls acknowledge exactly once per message")
        void acknowledgesExactlyOnce() {
            when(tracer.currentSpan()).thenReturn(null);

            handler.onOrderEvent(record("order-3", "{}"), acknowledgment);

            // verifyNoMoreInteractions would also pass; this is the explicit check
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("onOrderEvent — trace context access")
    class TraceContextAccess {

        @Test
        @DisplayName("reads traceId from the current span when one is present")
        void readsTraceIdFromCurrentSpan() {
            when(tracer.currentSpan()).thenReturn(currentSpan);
            when(currentSpan.context()).thenReturn(traceContext);
            when(traceContext.traceId()).thenReturn("cafebabe00000000cafebabe00000000");

            assertThatNoException().isThrownBy(() ->
                    handler.onOrderEvent(
                            record("order-4", "{\"orderId\":\"order-4\"}"), acknowledgment));

            verify(currentSpan).context();
            verify(traceContext).traceId();
        }

        @Test
        @DisplayName("does not access span context when currentSpan() returns null")
        void doesNotAccessContextWhenSpanNull() {
            when(tracer.currentSpan()).thenReturn(null);

            handler.onOrderEvent(record("order-5", "{}"), acknowledgment);

            // If the handler had called span.context() on a null span it would
            // have thrown; the fact that the test passes proves the null guard works.
            verify(currentSpan, never()).context();
        }
    }

    @Nested
    @DisplayName("onOrderEvent — payload handling")
    class PayloadHandling {

        @Test
        @DisplayName("processes message without throwing for arbitrary JSON payload")
        void handlesArbitraryJsonPayload() {
            when(tracer.currentSpan()).thenReturn(null);

            assertThatNoException().isThrownBy(() ->
                    handler.onOrderEvent(
                            record("order-6",
                                    "{\"orderId\":\"order-6\",\"amount\":99.99,\"currency\":\"USD\"}"),
                            acknowledgment));
        }

        @Test
        @DisplayName("processes message without throwing for empty payload")
        void handlesEmptyPayload() {
            when(tracer.currentSpan()).thenReturn(null);

            assertThatNoException().isThrownBy(() ->
                    handler.onOrderEvent(record("order-7", ""), acknowledgment));
        }
    }
}
