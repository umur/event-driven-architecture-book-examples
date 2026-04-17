package com.umurinan.eda.ch05.store;

import com.umurinan.eda.ch05.domain.events.OrderEvent;

import java.util.List;

public interface EventStore {

    /**
     * Appends a single event for the given aggregate.
     *
     * @param aggregateId      the aggregate identifier
     * @param event            the domain event to store
     * @param expectedSequence the sequence number this event will occupy; must not already exist
     *                         for this aggregateId (enforced by a unique constraint)
     */
    void append(String aggregateId, OrderEvent event, long expectedSequence);

    /**
     * Returns all events for the given aggregate in ascending sequence order.
     */
    List<OrderEvent> loadEvents(String aggregateId);
}
