package com.umurinan.eda.ch11;

import com.umurinan.eda.ch11.avro.OrderPlacedV1;
import com.umurinan.eda.ch11.avro.OrderPlacedV2;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip serialization tests using the Avro-generated Java classes.
 * No Spring context, no Kafka broker — purely in-process byte encoding.
 */
class AvroSerializationTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private byte[] serializeV1(OrderPlacedV1 event) throws Exception {
        var writer = new SpecificDatumWriter<>(OrderPlacedV1.class);
        var out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        writer.write(event, encoder);
        encoder.flush();
        return out.toByteArray();
    }

    private OrderPlacedV1 deserializeV1(byte[] bytes) throws Exception {
        var reader = new SpecificDatumReader<>(OrderPlacedV1.class);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(
                new ByteArrayInputStream(bytes), null);
        return reader.read(null, decoder);
    }

    private byte[] serializeV2(OrderPlacedV2 event) throws Exception {
        var writer = new SpecificDatumWriter<>(OrderPlacedV2.class);
        var out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        writer.write(event, encoder);
        encoder.flush();
        return out.toByteArray();
    }

    private OrderPlacedV2 deserializeV2FromV1Bytes(byte[] v1Bytes) throws Exception {
        // Use the V1 schema as writer, V2 schema as reader — this is the
        // backward-compatibility path that Avro's ResolvingGrammar handles.
        SpecificDatumReader<OrderPlacedV2> reader = new SpecificDatumReader<>(
                OrderPlacedV1.getClassSchema(),   // writer schema (what produced the bytes)
                OrderPlacedV2.getClassSchema());  // reader schema (what we want to materialise)
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(
                new ByteArrayInputStream(v1Bytes), null);
        return reader.read(null, decoder);
    }

    private OrderPlacedV2 deserializeV2(byte[] bytes) throws Exception {
        var reader = new SpecificDatumReader<>(OrderPlacedV2.class);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(
                new ByteArrayInputStream(bytes), null);
        return reader.read(null, decoder);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("V1 serialises and deserialises with all fields intact")
    void v1RoundTrip() throws Exception {
        var original = OrderPlacedV1.newBuilder()
                .setOrderId("ORD-001")
                .setCustomerId("CUST-42")
                .setTotal(99.95)
                .setPlacedAt(System.currentTimeMillis())
                .build();

        byte[] bytes = serializeV1(original);
        var restored = deserializeV1(bytes);

        assertThat(restored.getOrderId().toString()).isEqualTo("ORD-001");
        assertThat(restored.getCustomerId().toString()).isEqualTo("CUST-42");
        assertThat(restored.getTotal()).isEqualTo(99.95);
        assertThat(restored.getPlacedAt()).isEqualTo(original.getPlacedAt());
    }

    @Test
    @DisplayName("V1 bytes deserialized with V2 reader — discountCode is null (backward compat)")
    void v1BytesDeserializedByV2ReaderHasNullDiscountCode() throws Exception {
        var v1Event = OrderPlacedV1.newBuilder()
                .setOrderId("ORD-002")
                .setCustomerId("CUST-99")
                .setTotal(49.00)
                .setPlacedAt(System.currentTimeMillis())
                .build();

        byte[] v1Bytes = serializeV1(v1Event);
        var v2View = deserializeV2FromV1Bytes(v1Bytes);

        assertThat(v2View.getOrderId().toString()).isEqualTo("ORD-002");
        assertThat(v2View.getCustomerId().toString()).isEqualTo("CUST-99");
        assertThat(v2View.getTotal()).isEqualTo(49.00);
        // The new optional field defaults to null when reading old data
        assertThat(v2View.getDiscountCode()).isNull();
    }

    @Test
    @DisplayName("V2 serialises discountCode and deserialises it correctly")
    void v2RoundTripWithDiscountCode() throws Exception {
        var v2Event = OrderPlacedV2.newBuilder()
                .setOrderId("ORD-003")
                .setCustomerId("CUST-07")
                .setTotal(199.00)
                .setPlacedAt(System.currentTimeMillis())
                .setDiscountCode("SAVE10")
                .build();

        byte[] bytes = serializeV2(v2Event);
        var restored = deserializeV2(bytes);

        assertThat(restored.getOrderId().toString()).isEqualTo("ORD-003");
        assertThat(restored.getDiscountCode().toString()).isEqualTo("SAVE10");
    }

    @Test
    @DisplayName("V2 serialises null discountCode and round-trips cleanly")
    void v2RoundTripWithoutDiscountCode() throws Exception {
        var v2Event = OrderPlacedV2.newBuilder()
                .setOrderId("ORD-004")
                .setCustomerId("CUST-11")
                .setTotal(29.99)
                .setPlacedAt(System.currentTimeMillis())
                .setDiscountCode(null)
                .build();

        byte[] bytes = serializeV2(v2Event);
        var restored = deserializeV2(bytes);

        assertThat(restored.getOrderId().toString()).isEqualTo("ORD-004");
        assertThat(restored.getDiscountCode()).isNull();
    }
}
