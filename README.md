# Event-Driven Architecture Book Examples

This repository contains runnable code examples for the book **Event-Driven Architecture with Spring Boot 4.x and Kafka 4.x**.

Authors: Umur Inan, Muhyidean AlTarawneh

## What This Repo Is

- Multi-module Maven build.
- Each `chapter-XX-*` module is a focused Spring Boot project (or demo) for that chapter.
- Runtime dependencies vary by chapter; see the module `README.md` for details.

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (only for chapters that start Kafka and other services via Docker Compose)

## Quick Start

Build all modules:

```bash
mvn -q -DskipTests package
```

Run a single module:

```bash
mvn -pl chapter-07-sagas -am spring-boot:run
```

## Modules

| Module | Topic |
|---|---|
| `chapter-03-kafka-basics` | KafkaTemplate, @KafkaListener, DLTs |
| `chapter-04-cqrs` | CQRS (write model + projection) |
| `chapter-05-event-sourcing` | Event sourcing fundamentals |
| `chapter-06-cqrs-event-sourcing` | CQRS + event sourcing together |
| `chapter-07-sagas` | Sagas (orchestration) |
| `chapter-08-consumer-lag` | Lag measurement + consistency window |
| `chapter-09-outbox-pattern` | Transactional outbox |
| `chapter-10-idempotency` | Idempotent consumers + deduplication |
| `chapter-11-schema-evolution` | Schema evolution patterns |
| `chapter-12-observability` | Tracing/logging/metrics patterns |
| `chapter-13-testing-patterns` | Testing event-driven systems |
| `chapter-14-adaptive-routing` | Routing/cache/coherence patterns |

## License

MIT. See `LICENSE`.
