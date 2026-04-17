package com.umurinan.eda.ch12;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderEventPublisher}.
 *
 * The real Micrometer {@code Tracer.nextSpan()} returns a {@code Span} whose
 * {@code name()} and {@code start()} methods also return {@code Span} — there
 * is no separate {@code Span.Builder} in the fluent chain used by the
 * publisher.  All Kafka and tracing collaborators are mocked so these tests
 * run without an embedded broker or a Spring application context.
 */
@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private TraceContext traceContext;

    @Mock
    private Tracer.SpanInScope spanInScope;

    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafkaTemplate, tracer);

        // tracer.nextSpan() → span  (Span.name() and Span.start() both return Span)
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);

        // tracer.withSpan(span) → closeable scope
        when(tracer.withSpan(span)).thenReturn(spanInScope);

        // span.context().traceId() is used inside the log statement
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("abcdef1234567890abcdef1234567890");

        // kafkaTemplate.send must return a future so the call does not blow up
        RecordMetadata meta = new RecordMetadata(
                new TopicPartition("order-events", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(null, meta);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @Nested
    @DisplayName("publishOrderPlaced — span lifecycle")
    class SpanLifecycle {

        @Test
        @DisplayName("names the span 'order.placed.publish'")
        void namesSpanCorrectly() {
            publisher.publishOrderPlaced("order-123");

            verify(span).name("order.placed.publish");
        }

        @Test
        @DisplayName("starts the span before sending")
        void startsSpan() {
            publisher.publishOrderPlaced("order-123");

            verify(span).start();
        }

        @Test
        @DisplayName("activates the span scope around the send")
        void activatesScope() {
            publisher.publishOrderPlaced("order-123");

            verify(tracer).withSpan(span);
        }

        @Test
        @DisplayName("closes the span scope after sending")
        void closesScope() {
            publisher.publishOrderPlaced("order-123");

            verify(spanInScope).close();
        }

        @Test
        @DisplayName("ends the span after a successful send")
        void endsSpanAfterSend() {
            publisher.publishOrderPlaced("order-100");

            verify(span).end();
        }

        @Test
        @DisplayName("ends the span even when KafkaTemplate.send throws")
        void endsSpanEvenIfSendThrows() {
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("broker unavailable"));

            try {
                publisher.publishOrderPlaced("order-fail");
            } catch (RuntimeException ignored) {
                // expected — we only care that span.end() was still called
            }

            verify(span).end();
        }
    }

    @Nested
    @DisplayName("publishOrderPlaced — Kafka message")
    class KafkaMessage {

        @Test
        @DisplayName("sends to the 'order-events' topic with the order ID as key")
        void sendsToCorrectTopicAndKey() {
            publisher.publishOrderPlaced("order-456");

            verify(kafkaTemplate).send(eq("order-events"), eq("order-456"), anyString());
        }

        @Test
        @DisplayName("payload JSON contains the order ID")
        void payloadContainsOrderId() {
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

            publisher.publishOrderPlaced("order-789");

            verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue()).contains("order-789");
        }

        @Test
        @DisplayName("does not throw on a successful publish")
        void doesNotThrowOnHappyPath() {
            assertThatNoException().isThrownBy(() -> publisher.publishOrderPlaced("order-ok"));
        }
    }
}
