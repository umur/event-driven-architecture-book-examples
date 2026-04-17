# Chapter 13 — Testing Patterns for Event-Driven Systems

## What this module demonstrates

The test classes ARE the product. Each file is a self-contained example of one testing pattern for Kafka-based systems. Read them in order.

---

## Test inventory

| File | Pattern | Infrastructure |
|---|---|---|
| `EmbeddedKafkaExampleTest` | Basic async integration test | EmbeddedKafka + Awaitility |
| `DeadLetterTopicExampleTest` | DLT verification | EmbeddedKafka + Awaitility |
| `IdempotencyExampleTest` | Duplicate delivery | EmbeddedKafka + Awaitility |
| `BatchProcessingExampleTest` | Burst publish / all-processed assertion | EmbeddedKafka + Awaitility |
| `TestcontainersExampleTest` | Same test, real broker | Testcontainers (Docker) |

---

## EmbeddedKafka vs Testcontainers

**Use EmbeddedKafka when:**
- You want fast feedback (sub-second broker startup).
- Your consumer logic does not rely on broker-side features (compaction, transactions, quotas).
- You are running tests in a CI environment without Docker.

**Use Testcontainers when:**
- You use Kafka transactions or exactly-once semantics.
- You need to verify custom broker configuration.
- You want your test environment to match production as closely as possible.
- A bug was previously masked by EmbeddedKafka's simplified broker behaviour.

Both approaches use the same test structure. The only difference is how `spring.kafka.bootstrap-servers` is supplied.

---

## Awaitility vs Thread.sleep

`Thread.sleep(5000)` always waits the full 5 seconds. It gives no information when it times out, and it slows the suite down even when messages arrive in milliseconds.

Awaitility polls until the assertion passes or the deadline is hit:

```java
await()
    .atMost(10, SECONDS)
    .untilAsserted(() -> assertThat(handler.processedOrders).containsKey(orderId));
```

When the deadline is hit without the condition being satisfied, Awaitility reports the last assertion failure — not a generic timeout message.

---

## Running the tests

```bash
# EmbeddedKafka tests only (no Docker required)
./mvnw test -pl chapter-13-testing-patterns -Dtest="Embedded*,Dlt*,Idempotency*,Batch*"

# Testcontainers test (requires Docker)
./mvnw test -pl chapter-13-testing-patterns -Dtest="Testcontainers*"

# All tests
./mvnw test -pl chapter-13-testing-patterns
```
