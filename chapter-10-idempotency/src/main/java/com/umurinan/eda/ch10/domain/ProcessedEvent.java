package com.umurinan.eda.ch10.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(unique = true, nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(UUID idempotencyKey) {
        this.id = idempotencyKey;
        this.processedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Instant getProcessedAt() { return processedAt; }
}
