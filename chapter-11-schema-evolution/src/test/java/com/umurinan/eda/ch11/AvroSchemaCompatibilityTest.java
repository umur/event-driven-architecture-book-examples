package com.umurinan.eda.ch11;

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaPairCompatibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Avro schema backward-compatibility rules using inline schema
 * definitions.
 *
 * Key rule: schema compatibility checks in Avro require the reader and writer
 * to share the same full record name.  In production, schemas evolve in-place
 * (same name, same namespace) — version numbers live in the Schema Registry
 * metadata, not in the record name.  These tests use a single canonical name
 * "OrderPlaced" to demonstrate what "backward compatible" means structurally.
 *
 * Backward compatibility: a NEW reader schema can read data written with an
 * OLD writer schema.  Every field added by the new schema must have a default.
 */
class AvroSchemaCompatibilityTest {

    private static final String NAMESPACE = "com.umurinan.eda.ch11.avro";

    // Base schema — the V1 shape that is "already in production"
    private static final String V1_JSON = """
            {
              "namespace": "com.umurinan.eda.ch11.avro",
              "type": "record",
              "name": "OrderPlaced",
              "fields": [
                {"name": "orderId",    "type": "string"},
                {"name": "customerId", "type": "string"},
                {"name": "total",      "type": "double"},
                {"name": "placedAt",   "type": "long"}
              ]
            }
            """;

    // V2 adds discountCode with a null default — backward compatible
    private static final String V2_WITH_DEFAULT_JSON = """
            {
              "namespace": "com.umurinan.eda.ch11.avro",
              "type": "record",
              "name": "OrderPlaced",
              "fields": [
                {"name": "orderId",      "type": "string"},
                {"name": "customerId",   "type": "string"},
                {"name": "total",        "type": "double"},
                {"name": "placedAt",     "type": "long"},
                {"name": "discountCode", "type": ["null", "string"], "default": null}
              ]
            }
            """;

    // V2 adds discountCode WITHOUT a default — NOT backward compatible
    private static final String V2_NO_DEFAULT_JSON = """
            {
              "namespace": "com.umurinan.eda.ch11.avro",
              "type": "record",
              "name": "OrderPlaced",
              "fields": [
                {"name": "orderId",      "type": "string"},
                {"name": "customerId",   "type": "string"},
                {"name": "total",        "type": "double"},
                {"name": "placedAt",     "type": "long"},
                {"name": "discountCode", "type": "string"}
              ]
            }
            """;

    // Schema that drops customerId — reading old V1 data with this reader
    // fails: the writer produced customerId bytes, reader has no field for it
    // and there is no aliases/default resolution path.
    private static final String MISSING_FIELD_JSON = """
            {
              "namespace": "com.umurinan.eda.ch11.avro",
              "type": "record",
              "name": "OrderPlaced",
              "fields": [
                {"name": "orderId",  "type": "string"},
                {"name": "total",    "type": "double"},
                {"name": "placedAt", "type": "long"}
              ]
            }
            """;

    @Test
    @DisplayName("Adding a nullable field with default null is backward compatible")
    void addingNullableFieldWithDefaultIsCompatible() {
        var schemaV1 = new Schema.Parser().parse(V1_JSON);
        var schemaV2 = new Schema.Parser().parse(V2_WITH_DEFAULT_JSON);

        // reader = V2 (new schema), writer = V1 (old data already in Kafka)
        SchemaPairCompatibility result =
                SchemaCompatibility.checkReaderWriterCompatibility(schemaV2, schemaV1);

        assertThat(result.getType())
                .as("V2 reader (with nullable discountCode default null) must read V1 data")
                .isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }

    @Test
    @DisplayName("Adding a required field without default breaks backward compatibility")
    void addingRequiredFieldWithoutDefaultIsIncompatible() {
        var schemaV1 = new Schema.Parser().parse(V1_JSON);
        var schemaV2NoDefault = new Schema.Parser().parse(V2_NO_DEFAULT_JSON);

        // reader = V2 (requires discountCode), writer = V1 (never wrote it)
        // The reader cannot produce a value for discountCode → INCOMPATIBLE
        SchemaPairCompatibility result =
                SchemaCompatibility.checkReaderWriterCompatibility(schemaV2NoDefault, schemaV1);

        assertThat(result.getType())
                .as("A new required field without default cannot be read from old data")
                .isEqualTo(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE);
    }

    @Test
    @DisplayName("Removing a field that the reader still expects breaks backward compatibility")
    void removingFieldBreaksCompatibility() {
        var schemaV1 = new Schema.Parser().parse(V1_JSON);
        var reduced = new Schema.Parser().parse(MISSING_FIELD_JSON);

        // reader = V1 (expects customerId), writer = reduced (never writes it,
        // and reduced has no default for it either)
        SchemaPairCompatibility result =
                SchemaCompatibility.checkReaderWriterCompatibility(schemaV1, reduced);

        assertThat(result.getType())
                .as("Reader expecting customerId cannot read data written by a schema that dropped it")
                .isEqualTo(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE);
    }
}
