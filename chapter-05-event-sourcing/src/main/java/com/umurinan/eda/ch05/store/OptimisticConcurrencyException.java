package com.umurinan.eda.ch05.store;

/**
 * Thrown when two writers race to append an event at the same sequence number
 * for the same aggregate. The caller must reload the aggregate and retry.
 */
public class OptimisticConcurrencyException extends RuntimeException {

    private final String aggregateId;
    private final long expectedSequence;

    public OptimisticConcurrencyException(String aggregateId, long expectedSequence, Throwable cause) {
        super(String.format(
                "Sequence conflict on aggregate '%s': sequence %d already exists",
                aggregateId, expectedSequence), cause);
        this.aggregateId = aggregateId;
        this.expectedSequence = expectedSequence;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public long getExpectedSequence() {
        return expectedSequence;
    }
}
