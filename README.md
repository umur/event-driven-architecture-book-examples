# Event-Driven Architecture with Spring Boot 4 and Kafka 4

> Patterns, pitfalls, and production realities of building event-driven systems.

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?logo=spring&logoColor=white) ![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-4.0-231F20?logo=apachekafka&logoColor=white) ![License: MIT](https://img.shields.io/badge/License%3A_MIT-MIT-blue)

Companion code for **Event-Driven Architecture with Spring Boot 4 and Kafka 4** by [Umur Inan](https://umurinan.com), and Muhyidean AlTarawneh.

## About the book

A deep-dive into building reliable event-driven systems with Apache Kafka 4 and Spring Boot 4. The running domain is an e-commerce order flow (order placement, payment, inventory, shipment, notification) wired across CQRS, event sourcing, sagas, the outbox pattern, idempotent consumers, schema evolution, and observability. Every pattern is backed by a runnable Spring Boot project with the failure modes and remediation paths spelled out.

## Who this is for

- Spring Boot engineers building their first Kafka-based system and hitting consistency problems
- Architects evaluating CQRS, event sourcing, and sagas for a real production system
- Engineers who have deployed Kafka but are still losing messages or fighting schema compatibility

## Prerequisites

- Java 21 LTS ([Temurin](https://adoptium.net))
- Maven 3.9+ (or use the bundled `./mvnw` wrapper)
- Docker and Docker Compose

## Quick start

```bash
git clone https://github.com/umur/event-driven-architecture-book-examples
cd event-driven-architecture-book-examples/chapter-02-kafka-basics
docker compose up -d
mvn spring-boot:run
```

Chapter 2 brings up a single-broker KRaft Kafka cluster on `localhost:9092`. All subsequent chapter modules connect to that broker.

## Chapters

Each `chapter-NN-slug/` directory is a self-contained, runnable Spring Boot project. Each chapter is a cumulative snapshot: it builds on the previous chapter's state plus that chapter's specific change. Each chapter directory has its own `README.md` with the delta and run instructions.

- `chapter-02-kafka-basics`: Kafka fundamentals, `KafkaTemplate`, `@KafkaListener`, and DLTs
- `chapter-03-spring-boot-kafka`: Spring Boot 4 and Spring Kafka wiring end to end
- `chapter-04-cqrs`: CQRS write-side and read-side split
- `chapter-05-event-sourcing`: event sourcing fundamentals and replay
- `chapter-06-cqrs-event-sourcing`: combined CQRS and event sourcing
- `chapter-07-sagas`: saga orchestration across services
- `chapter-08-consumer-lag`: measuring lag and the consistency window
- `chapter-09-outbox-pattern`: transactional outbox and relay
- `chapter-10-idempotency`: idempotent consumers and deduplication
- `chapter-11-schema-evolution`: Avro and schema evolution patterns
- `chapter-12-observability`: tracing, logging, and metrics for event flows
- `chapter-13-testing-patterns`: testing event-driven systems
- `chapter-14-adaptive-routing`: routing, cache coherence, and adaptive patterns
- `chapter-15-migration`: migrating an existing system to event-driven architecture
- `chapter-16-multi-tenant`: multi-tenant event-driven systems

Chapter 1 is prose-only and has no companion code.

## Stack

- Java 21 (LTS)
- Spring Boot 4.0.6
- Spring Kafka latest stable
- Apache Kafka 4.0 (KRaft mode, no ZooKeeper)
- PostgreSQL 16 (for chapters that need persistence)
- Testcontainers and `spring-kafka-test` for integration tests

## Related books

- [Microservices with Spring Boot 4](https://github.com/umur/microservices-example): the broader microservices context in which these Kafka patterns operate
- [Cloud-Native Spring Boot on Kubernetes](https://github.com/umur/cloud-native-spring-boot-example): operating Kafka on Kubernetes covered in Chapter 25
- [Spring Batch in Production](https://github.com/umur/spring-batch-example): transactional Kafka producers and consumers from a batch perspective

## About the author

I'm Umur Inan, a Principal Software Engineer with 15 years of experience building backend systems across enterprise, government, and high-growth environments. I specialize in microservices architecture, distributed systems, and cloud-native development, with deep expertise in Spring Boot, Kafka, and Kubernetes. Based in New York City, I've shipped products across five countries and hold a Master's in Computer Science and a Bachelor's in Computer Engineering.

[umurinan.com](https://umurinan.com)

## License

MIT. See [LICENSE](LICENSE).
