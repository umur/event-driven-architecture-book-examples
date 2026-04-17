package com.umurinan.eda.ch11;

/**
 * Unchecked wrapper for Avro serialization failures.
 */
public class AvroSerializationException extends RuntimeException {

    public AvroSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
