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
 * Unit tests for {@link TestTraceCapture}.
 *
 * Mirrors the structure of {@link HeaderCapturingConsumerTest} because the two
 * classes have the same capture / reset contract but listen on different topics.
 */
class TestTraceCaptureTest {

    private TestTraceCapture capture;

    @BeforeEach
    void setUp() {
        capture = new TestTraceCapture();
    }

    private ConsumerRecord<String, String> recordWithHeader(String headerValue) {
        RecordHeaders headers = new RecordHeaders();
        if (headerValue != null) {
            headers.add("traceparent", headerValue.getBytes(StandardCharsets.UTF_8));
        }
        return new ConsumerRecord<>(
                "order-events-trace-test", 0, 0L,
                ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
                0, 0,
                "order-001", "{\"orderId\":\"order-001\"}",
                headers, Optional.empty());
    }

    private static final Acknowledgment NOOP_ACK = () -> {};

    private void consume(ConsumerRecord<String, String> record) {
        capture.onMessage(record, NOOP_ACK);
    }

    @Nested
    @DisplayName("initial state")
    class InitialState {

        @Test
        @DisplayName("capturedTraceparent() is null before any message arrives")
        void nullBeforeFirstMessage() {
            assertThat(capture.capturedTraceparent()).isNull();
        }
    }

    @Nested
    @DisplayName("onMessage — traceparent header present")
    class HeaderPresent {

        @Test
        @DisplayName("stores the full traceparent string verbatim")
        void storesTraceparentVerbatim() {
            String tp = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

            consume(recordWithHeader(tp));

            assertThat(capture.capturedTraceparent()).isEqualTo(tp);
        }

        @Test
        @DisplayName("extracted traceId segment (index 1) is 32 lowercase hex characters")
        void traceIdSegmentIs32HexChars() {
            consume(recordWithHeader(
                    "00-cafebabe00000000cafebabe00000000-deadbeef00000001-01"));

            String[] parts = capture.capturedTraceparent().split("-");
            assertThat(parts).hasSize(4);
            assertThat(parts[1])
                    .hasSize(32)
                    .matches("[0-9a-f]+");
        }

        @Test
        @DisplayName("later message overwrites the earlier captured value")
        void laterMessageOverwritesEarlier() {
            consume(recordWithHeader(
                    "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-00"));
            consume(recordWithHeader(
                    "00-cccccccccccccccccccccccccccccccc-dddddddddddddddd-01"));

            assertThat(capture.capturedTraceparent())
                    .startsWith("00-cccc");
        }
    }

    @Nested
    @DisplayName("onMessage — traceparent header absent")
    class HeaderAbsent {

        @Test
        @DisplayName("capturedTraceparent() stays null when header is missing")
        void staysNullWhenHeaderMissing() {
            consume(recordWithHeader(null));

            assertThat(capture.capturedTraceparent()).isNull();
        }

        @Test
        @DisplayName("existing capture is not overwritten by a headerless message")
        void existingCapturePreservedWhenHeaderMissing() {
            String original = "00-aabbccddeeff00112233445566778899-0102030405060708-01";
            consume(recordWithHeader(original));
            consume(recordWithHeader(null));

            assertThat(capture.capturedTraceparent()).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("reset() nulls out any previously captured value")
        void resetNullsOut() {
            consume(recordWithHeader(
                    "00-aabbccddeeff00112233445566778899-0102030405060708-01"));
            capture.reset();

            assertThat(capture.capturedTraceparent()).isNull();
        }

        @Test
        @DisplayName("reset() on a fresh instance is a safe no-op")
        void resetOnFreshInstanceIsNoOp() {
            capture.reset();

            assertThat(capture.capturedTraceparent()).isNull();
        }

        @Test
        @DisplayName("can capture again after reset")
        void canCaptureAgainAfterReset() {
            consume(recordWithHeader(
                    "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-00"));
            capture.reset();

            String second = "00-cccccccccccccccccccccccccccccccc-dddddddddddddddd-01";
            consume(recordWithHeader(second));

            assertThat(capture.capturedTraceparent()).isEqualTo(second);
        }
    }
}
