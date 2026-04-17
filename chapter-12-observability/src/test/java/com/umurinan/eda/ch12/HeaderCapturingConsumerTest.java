package com.umurinan.eda.ch12;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HeaderCapturingConsumer}.
 *
 * Exercises the capture-and-reset lifecycle and the header extraction logic
 * without requiring a Kafka broker.
 */
class HeaderCapturingConsumerTest {

    private HeaderCapturingConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new HeaderCapturingConsumer();
    }

    // ------------------------------------------------------------------
    // Helper: build a ConsumerRecord with optional traceparent header
    // ------------------------------------------------------------------
    private ConsumerRecord<String, String> recordWithHeader(String headerValue) {
        RecordHeaders headers = new RecordHeaders();
        if (headerValue != null) {
            headers.add("traceparent", headerValue.getBytes(StandardCharsets.UTF_8));
        }
        return new ConsumerRecord<>(
                "trace-header-test", 0, 0L,
                ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
                0, 0,
                "key", "value",
                headers, Optional.empty());
    }

    private static final Acknowledgment NOOP_ACK = () -> {};

    private void consume(ConsumerRecord<String, String> record) {
        consumer.onMessage(record, NOOP_ACK);
    }

    @Nested
    @DisplayName("initial state")
    class InitialState {

        @Test
        @DisplayName("capturedTraceparent() is null before any message is received")
        void nullBeforeAnyMessage() {
            assertThat(consumer.capturedTraceparent()).isNull();
        }
    }

    @Nested
    @DisplayName("onMessage — header present")
    class HeaderPresent {

        @Test
        @DisplayName("captures a well-formed W3C traceparent header")
        void capturesWellFormedTraceparent() {
            String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

            consume(recordWithHeader(traceparent));

            assertThat(consumer.capturedTraceparent()).isEqualTo(traceparent);
        }

        @Test
        @DisplayName("overwrites the previous capture when a second message arrives")
        void overwritesPreviousCapture() {
            consume(recordWithHeader(
                    "00-aabbccddeeff00112233445566778899-0102030405060708-00"));
            consume(recordWithHeader(
                    "00-11223344556677889900aabbccddeeff-0807060504030201-01"));

            assertThat(consumer.capturedTraceparent())
                    .isEqualTo("00-11223344556677889900aabbccddeeff-0807060504030201-01");
        }
    }

    @Nested
    @DisplayName("onMessage — header absent")
    class HeaderAbsent {

        @Test
        @DisplayName("capturedTraceparent() remains null when no traceparent header is present")
        void remainsNullWithoutHeader() {
            consume(recordWithHeader(null));

            assertThat(consumer.capturedTraceparent()).isNull();
        }

        @Test
        @DisplayName("does not overwrite a previously captured value when a headerless message arrives")
        void doesNotOverwriteExistingCaptureWhenHeaderMissing() {
            String first = "00-aabbccddeeff00112233445566778899-0102030405060708-01";
            consume(recordWithHeader(first));

            // second message has no traceparent header
            consume(recordWithHeader(null));

            assertThat(consumer.capturedTraceparent()).isEqualTo(first);
        }
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("reset() clears a previously captured value")
        void resetClearsCapture() {
            consume(recordWithHeader(
                    "00-aabbccddeeff00112233445566778899-0102030405060708-01"));
            assertThat(consumer.capturedTraceparent()).isNotNull();

            consumer.reset();

            assertThat(consumer.capturedTraceparent()).isNull();
        }

        @Test
        @DisplayName("reset() on a never-written consumer is a no-op")
        void resetOnFreshConsumerIsNoOp() {
            consumer.reset();

            assertThat(consumer.capturedTraceparent()).isNull();
        }

        @Test
        @DisplayName("capture works normally after a reset")
        void captureWorksAfterReset() {
            consume(recordWithHeader(
                    "00-aabbccddeeff00112233445566778899-0102030405060708-01"));
            consumer.reset();

            String second = "00-cafebabe00000000cafebabe00000000-deadbeef00000001-01";
            consume(recordWithHeader(second));

            assertThat(consumer.capturedTraceparent()).isEqualTo(second);
        }
    }
}
