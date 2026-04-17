CREATE TABLE IF NOT EXISTS order_events (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id     VARCHAR(255)  NOT NULL,
    event_type       VARCHAR(100)  NOT NULL,
    payload          TEXT          NOT NULL,
    sequence_number  BIGINT        NOT NULL,
    occurred_at      TIMESTAMP     NOT NULL,
    CONSTRAINT uq_aggregate_sequence UNIQUE (aggregate_id, sequence_number)
);
