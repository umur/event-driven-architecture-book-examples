# Chapter 10 — Idempotency

## At-least-once delivery

Kafka guarantees that a message is delivered at least once. Under normal conditions a consumer reads a message, processes it, and commits the offset. But if the consumer crashes after processing and before committing, Kafka re-delivers the message on restart. The consumer then processes it a second time.

For side-effect-free reads this is harmless. For operations with consequences — charging a payment, sending an email, updating an inventory count — a second execution causes real damage.

## Idempotency key pattern

The solution is to make the consumer idempotent: processing the same message twice produces the same outcome as processing it once.

Each `PaymentRequest` event carries a `UUID idempotencyKey` set by the producer. The consumer uses a `processed_events` table as a deduplication log:

```
┌───────────────────────────────────────────────┐
│       IdempotentPaymentHandler                 │
│  @Transactional                                │
│                                                │
│  1. SELECT FROM processed_events WHERE id = ?  │
│     → found: return ALREADY_PROCESSED          │
│     → not found: continue                      │
│  2. paymentGateway.charge(orderId, amount)     │
│  3. INSERT INTO processed_events (id, ...) ... │
└───────────────────────────────────────────────┘
```

The check, the charge, and the insert are wrapped in a single `@Transactional` boundary. If the service crashes after the charge but before the insert commits, the transaction rolls back and the charge did not happen — the next delivery will process cleanly. If it crashes after commit, the next delivery finds the key in `processed_events` and skips the charge.

## Deduplication table

`processed_events` has a single primary key column — the idempotency key UUID. The primary key constraint is the only guard needed: if two concurrent threads race on the same key, one insert succeeds and the other throws `DataIntegrityViolationException`, which Spring translates into a rollback.

## Running the tests

```bash
mvn test -pl chapter-10-idempotency
```

- `IdempotentPaymentHandlerTest` — unit test with mocks, verifies skip-on-duplicate and always-returns-result
- `DeduplicationConstraintTest` — `@DataJpaTest`, verifies the primary key constraint rejects duplicate UUIDs
- `DuplicateDeliveryTest` — `@SpringBootTest @EmbeddedKafka`, publishes the same message twice, asserts `charge()` called exactly once
