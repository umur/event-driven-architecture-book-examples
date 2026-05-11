# Event-Driven Architecture with Spring Boot 4 & Kafka 4

> Patterns, pitfalls, and production realities of building event-driven systems.

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?logo=spring&logoColor=white) ![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-4.0-231F20?logo=apachekafka&logoColor=white) ![License: MIT](https://img.shields.io/badge/License%3A_MIT-MIT-blue)

Companion code for **Event-Driven Architecture with Spring Boot 4.x and Kafka 4.x** by [Umur Inan](https://umurinan.com) and Muhyidean AlTarawneh.

## About the book

A deep-dive into building reliable event-driven systems with Apache Kafka 4 and Spring Boot 4. CQRS, event sourcing, sagas, the outbox pattern, idempotent consumers, schema evolution, observability. Every pattern backed by runnable Spring Boot examples, with the failure modes and remediation paths spelled out.

## Who this is for

- Spring Boot engineers building their first Kafka-based system and hitting consistency problems
- Architects evaluating CQRS, event sourcing, and sagas for a real production system
- Engineers who have deployed Kafka but are still losing messages or fighting schema compatibility

## Chapters

1. Why Event-Driven Architecture?
2. Apache Kafka from the Ground Up
3. Spring Boot 4 and Kafka
4. CQRS: Separating Reads from Writes
5. Event Sourcing
6. CQRS and Event Sourcing Together
7. The Saga Pattern
8. Eventual Consistency
9. The Outbox Pattern
10. Idempotency and Duplicate Handling
11. Schema Evolution
12. Observability
13. Testing Event-Driven Systems
14. Cache Coherence in Distributed Read Models
15. Migrating to Event-Driven Architecture
16. Multi-Tenant Event-Driven Systems

## Prerequisites

- Java 21 LTS ([Temurin](https://adoptium.net))
- Maven 3.9+
- Docker & Docker Compose (Kafka, Postgres)

## Quick start

```bash
git clone https://github.com/umur/event-driven-architecture-book-examples
cd event-driven-architecture-book-examples/chapter-02-kafka-basics
docker-compose up -d
mvn spring-boot:run
```

Chapter 2 brings up a single-broker KRaft Kafka cluster on `localhost:9092`. All subsequent chapter modules connect to that broker.

## Layout

Multi-module Maven build. Each `chapter-XX-topic/` module is a self-contained Spring Boot project:

- `chapter-02-kafka-basics`: Kafka fundamentals, KafkaTemplate, @KafkaListener, DLTs
- `chapter-04-cqrs`: CQRS write-side / read-side split
- `chapter-05-event-sourcing`: event sourcing fundamentals
- `chapter-06-cqrs-event-sourcing`: combined CQRS + ES
- `chapter-07-sagas`: saga orchestration
- `chapter-08-consumer-lag`: measuring lag and the consistency window
- `chapter-09-outbox-pattern`: transactional outbox
- `chapter-10-idempotency`: idempotent consumers and deduplication
- `chapter-11-schema-evolution`: Avro and schema evolution patterns
- `chapter-12-observability`: tracing, logging, and metrics
- `chapter-13-testing-patterns`: testing event-driven systems
- `chapter-14-adaptive-routing`: routing, cache coherence, and adaptive patterns

## Stack

- Java 21 (LTS)
- Spring Boot 4.0.6
- Spring Kafka
- Apache Kafka 4.0 (KRaft mode)
- H2 in-memory database (for chapters that need persistence)
- Testcontainers + spring-kafka-test for integration tests

## Related books

- [Microservices with Spring Boot 4](https://github.com/umur/microservices-example): the broader microservices context in which these Kafka patterns operate
- [Cloud-Native Spring Boot on Kubernetes](https://github.com/umur/kubernetes-example): operating Kafka on Kubernetes covered in Chapter 25
- [Production Observability](https://github.com/umur/observability-example): observability for event-driven systems covered in Chapter 12 here and in depth there

## About the author

I'm Umur Inan. I write production-focused books about Java, Spring Boot, distributed systems, and everything that makes software reliable at scale.

[umurinan.com](https://umurinan.com)

## License

MIT. See [LICENSE](LICENSE).
