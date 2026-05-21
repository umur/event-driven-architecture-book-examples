package com.umurinan.eda.ch11;

import com.umurinan.eda.ch11.avro.OrderPlacedV1;
import com.umurinan.eda.ch11.avro.OrderPlacedV2;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * Consumes raw Avro bytes from {@code order-placed-avro} and deserializes
 * them using the V2 reader schema.
 *
 * Because V2 is backward compatible with V1 (the added {@code discountCode}
 * field has a null default), this single consumer handles both old V1 messages
 * already in the topic and new V2 messages transparently.  Avro's
 * ResolvingGrammar fills in the default value for any field the writer did
 * not include.
 */
@Service
public class AvroOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(AvroOrderConsumer.class);

    @KafkaListener(topics = "order-placed-avro", groupId = "avro-consumer")
    public void onMessage(byte[] payload) {
        try {
            // Use V1 as the writer schema so the resolver handles both versions.
            // When a true V2 payload arrives the resolver is a no-op for the
            // common fields and reads discountCode normally.
            SpecificDatumReader<OrderPlacedV2> reader = new SpecificDatumReader<>(
                    OrderPlacedV1.getClassSchema(),
                    OrderPlacedV2.getClassSchema());

            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(
                    new ByteArrayInputStream(payload), null);

            OrderPlacedV2 event = reader.read(null, decoder);

            log.info("Received order: orderId={} customerId={} discountCode={}",
                    event.getOrderId(),
                    event.getCustomerId(),
                    event.getDiscountCode());
        } catch (Exception e) {
            log.error("Failed to deserialize Avro message", e);
        }
    }
}
