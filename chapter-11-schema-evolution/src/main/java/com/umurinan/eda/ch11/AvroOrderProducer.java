package com.umurinan.eda.ch11;

import com.umurinan.eda.ch11.avro.OrderPlacedV1;
import com.umurinan.eda.ch11.avro.OrderPlacedV2;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

/**
 * Publishes Avro-serialized order events to {@code order-placed-avro}.
 *
 * The serialization is intentionally manual (no Confluent Schema Registry)
 * so the module stays self-contained and tests can run with EmbeddedKafka.
 * In production you would swap the byte[] serializer for the Confluent
 * KafkaAvroSerializer and gain centralized schema governance.
 */
@Service
public class AvroOrderProducer {

    private static final Logger log = LoggerFactory.getLogger(AvroOrderProducer.class);
    private static final String TOPIC = "order-placed-avro";

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public AvroOrderProducer(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Serialize an {@link OrderPlacedV1} event and publish it.
     * V1 messages carry no discountCode field.
     */
    public void sendOrderV1(String orderId, String customerId, double total) {
        var event = OrderPlacedV1.newBuilder()
                .setOrderId(orderId)
                .setCustomerId(customerId)
                .setTotal(total)
                .setPlacedAt(System.currentTimeMillis())
                .build();

        byte[] bytes = serialize(new SpecificDatumWriter<>(OrderPlacedV1.class), event);
        log.info("Sending V1 order: orderId={} customerId={} total={}", orderId, customerId, total);
        kafkaTemplate.send(TOPIC, orderId, bytes);
    }

    /**
     * Serialize an {@link OrderPlacedV2} event and publish it.
     * V2 adds the optional {@code discountCode} field.
     */
    public void sendOrderV2(String orderId, String customerId, double total, String discountCode) {
        var event = OrderPlacedV2.newBuilder()
                .setOrderId(orderId)
                .setCustomerId(customerId)
                .setTotal(total)
                .setPlacedAt(System.currentTimeMillis())
                .setDiscountCode(discountCode)
                .build();

        byte[] bytes = serialize(new SpecificDatumWriter<>(OrderPlacedV2.class), event);
        log.info("Sending V2 order: orderId={} customerId={} discountCode={}", orderId, customerId, discountCode);
        kafkaTemplate.send(TOPIC, orderId, bytes);
    }

    private <T> byte[] serialize(SpecificDatumWriter<T> writer, T record) {
        try {
            var out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            writer.write(record, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new AvroSerializationException("Failed to serialize Avro record", e);
        }
    }
}
