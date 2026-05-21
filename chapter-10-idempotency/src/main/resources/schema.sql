-- Schema for idempotency pattern demonstration
-- The primary key constraint on idempotency_key ensures that duplicate events are rejected
-- This is the core mechanism that makes the handler idempotent

CREATE TABLE processed_events (
    idempotency_key UUID NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    PRIMARY KEY (idempotency_key)
);

-- Additional index for cleanup/audit queries (optional but useful in production)
CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events(processed_at);
