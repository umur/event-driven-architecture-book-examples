package com.umurinan.eda.ch11;

import com.umurinan.eda.ch11.avro.OrderPlacedV1;
import com.umurinan.eda.ch11.avro.OrderPlacedV2;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test consumer for the backward-compatibility scenario:
 * bytes were written by a V1 producer and are read using the V2 reader schema.
 *
 * Uses the two-schema {@code SpecificDatumReader} constructor so Avro's
 * ResolvingGrammar fills in the null default for {@code discountCode}.
 */
@Component
public class TestAvroConsumerV1Compat {

    private final AtomicReference<OrderPlacedV2> captured = new AtomicReference<>();

    public void reset() {
        captured.set(null);
    }

    public OrderPlacedV2 captured() {
        return captured.get();
    }

    @KafkaListener(
            topics = "order-placed-v1-compat",
            groupId = "avro-v1-compat-test"
    )
    public void onMessage(byte[] payload) throws Exception {
        // writer = V1 schema, reader = V2 schema — backward compatibility path
        SpecificDatumReader<OrderPlacedV2> reader = new SpecificDatumReader<>(
                OrderPlacedV1.getClassSchema(),
                OrderPlacedV2.getClassSchema());
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(
                new ByteArrayInputStream(payload), null);
        captured.set(reader.read(null, decoder));
    }
}
