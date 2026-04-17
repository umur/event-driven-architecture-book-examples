# Chapter 09 — Outbox Pattern

## The dual-write problem

A service that saves to a database and publishes to Kafka performs two separate I/O operations. Any crash between them leaves the system inconsistent: the database row exists but the event was never published, or the event was published but the database write rolled back. Neither outcome is recoverable without manual intervention.

## The outbox pattern

The fix is to treat the event as data. Instead of sending to Kafka directly, the service writes an `OutboxMessage` row to the same database, inside the same transaction as the business entity. If the transaction commits, both the `Order` row and the `OutboxMessage` row are durable. If it rolls back, neither exists. The two writes are atomic by construction.

```
┌──────────────────────────────────────────┐
│           OrderService.placeOrder()       │
│  @Transactional                          │
│                                          │
│  1. INSERT INTO orders ...               │  ─┐
│  2. INSERT INTO outbox_messages ...      │   │ same transaction
└──────────────────────────────────────────┘  ─┘
                    │
                    │ commit
                    ▼
         ┌──────────────────┐
         │  OutboxPoller    │  @Scheduled(fixedDelay=500ms)
         │                  │
         │  SELECT unpublished rows
         │  kafkaTemplate.send(...)
         │  UPDATE published_at = now()
         └──────────────────┘
```

## Relay mechanism

`OutboxPoller` runs every 500 ms. It loads all rows where `published_at IS NULL`, ordered by `created_at`. For each row it calls `kafkaTemplate.send()` then stamps `published_at`. The query-and-stamp is itself wrapped in a transaction, so a crash mid-poll simply leaves the row unpublished and the poller retries on the next tick — giving at-least-once delivery to Kafka.

## Running the tests

```bash
mvn test -pl chapter-09-outbox-pattern
```

- `OutboxRepositoryTest` — `@DataJpaTest`, verifies the repository query and ordering
- `AtomicWriteTest` — `@SpringBootTest`, verifies both records are written in one unit of work
- `OutboxPollerIntegrationTest` — `@SpringBootTest @EmbeddedKafka`, end-to-end relay verification
