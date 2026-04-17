package com.umurinan.eda.ch11;

import com.umurinan.eda.ch11.avro.OrderPlacedV1;
import com.umurinan.eda.ch11.avro.OrderPlacedV2;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test: publish raw Avro bytes via KafkaTemplate and
 * verify that the consumers deserialise them correctly.
 *
 * Two separate topics are used to keep the two evolution scenarios isolated:
 *
 *   order-placed-v1-compat  — V1 bytes read with a V2 reader (backward compat)
 *   order-placed-v2         — V2 bytes read with a V2 reader (same version)
 *
 * Raw byte[] serialization avoids any Confluent Schema Registry dependency so
 * the tests run entirely in-process with EmbeddedKafka.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"order-placed-v1-compat", "order-placed-v2"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@Import({TestAvroConsumerV1Compat.class, TestAvroConsumerV2.class})
@DirtiesContext
class AvroKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Autowired
    private TestAvroConsumerV1Compat v1CompatConsumer;

    @Autowired
    private TestAvroConsumerV2 v2Consumer;

    @BeforeEach
    void resetCaptures() {
        v1CompatConsumer.reset();
        v2Consumer.reset();
    }

    @Test
    @DisplayName("V1 bytes consumed by V2 reader — discountCode is null (backward compat)")
    void v1MessageConsumedAsV2() throws Exception {
        var v1Event = OrderPlacedV1.newBuilder()
                .setOrderId("ORD-INT-001")
                .setCustomerId("CUST-INT-01")
                .setTotal(75.50)
                .setPlacedAt(System.currentTimeMillis())
                .build();

        byte[] v1Bytes = serialize(new SpecificDatumWriter<>(OrderPlacedV1.class), v1Event);
        kafkaTemplate.send("order-placed-v1-compat", "ORD-INT-001", v1Bytes);

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(v1CompatConsumer.captured()).isNotNull()
        );

        var received = v1CompatConsumer.captured();
        assertThat(received.getOrderId().toString()).isEqualTo("ORD-INT-001");
        assertThat(received.getCustomerId().toString()).isEqualTo("CUST-INT-01");
        assertThat(received.getTotal()).isEqualTo(75.50);
        // V1 data carries no discountCode — V2 reader fills it in with null default
        assertThat(received.getDiscountCode()).isNull();
    }

    @Test
    @DisplayName("V2 bytes consumed by V2 reader — discountCode is preserved")
    void v2MessageConsumedWithDiscountCode() throws Exception {
        var v2Event = OrderPlacedV2.newBuilder()
                .setOrderId("ORD-INT-002")
                .setCustomerId("CUST-INT-02")
                .setTotal(120.00)
                .setPlacedAt(System.currentTimeMillis())
                .setDiscountCode("WELCOME20")
                .build();

        byte[] v2Bytes = serialize(new SpecificDatumWriter<>(OrderPlacedV2.class), v2Event);
        kafkaTemplate.send("order-placed-v2", "ORD-INT-002", v2Bytes);

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(v2Consumer.captured()).isNotNull()
        );

        var received = v2Consumer.captured();
        assertThat(received.getOrderId().toString()).isEqualTo("ORD-INT-002");
        assertThat(received.getDiscountCode().toString()).isEqualTo("WELCOME20");
    }

    private <T> byte[] serialize(SpecificDatumWriter<T> writer, T record) throws Exception {
        var out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        writer.write(record, encoder);
        encoder.flush();
        return out.toByteArray();
    }
}
