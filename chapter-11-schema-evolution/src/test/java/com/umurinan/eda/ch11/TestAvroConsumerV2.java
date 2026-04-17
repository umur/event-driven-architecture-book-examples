package com.umurinan.eda.ch11;

import com.umurinan.eda.ch11.avro.OrderPlacedV2;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test consumer for the V2-native scenario:
 * bytes were written by a V2 producer and are read using the V2 reader schema.
 */
@Component
public class TestAvroConsumerV2 {

    private final AtomicReference<OrderPlacedV2> captured = new AtomicReference<>();

    public void reset() {
        captured.set(null);
    }

    public OrderPlacedV2 captured() {
        return captured.get();
    }

    @KafkaListener(
            topics = "order-placed-v2",
            groupId = "avro-v2-test"
    )
    public void onMessage(byte[] payload) throws Exception {
        // writer = V2 schema, reader = V2 schema — normal same-version path
        SpecificDatumReader<OrderPlacedV2> reader = new SpecificDatumReader<>(OrderPlacedV2.class);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(
                new ByteArrayInputStream(payload), null);
        captured.set(reader.read(null, decoder));
    }
}
