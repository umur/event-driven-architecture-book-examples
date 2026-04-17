# Chapter 04 — CQRS

## What is CQRS?

Command Query Responsibility Segregation (CQRS) is the practice of using a different model to
handle writes than you use to handle reads. Instead of one model that tries to serve both, you
split responsibilities cleanly:

- **Command side** — accepts mutations (create, update, deactivate), validates them, writes to
  the authoritative store, and emits an event for every state change.
- **Query side** — listens for those events and maintains a denormalized read model that is
  optimized for how clients actually query the data.

The two sides never share a database connection. The read model is always slightly behind the
write model (eventual consistency), but it can be shaped independently — indexed differently,
projected differently, even stored in a different database engine.

## What this example shows

| Concept | Where to look |
|---|---|
| Write model (Product entity) | `domain/Product.java`, `ProductRepository.java` |
| Command handler | `ProductCommandHandler.java` |
| Domain events | `events/ProductCreated.java`, `events/ProductPriceUpdated.java`, `events/ProductDeactivated.java` |
| Read model (ProductListing entity) | `domain/ProductListing.java`, `ProductListingRepository.java` |
| Projection (event consumer) | `ProductProjection.java` |
| Kafka wiring | `config/KafkaConfig.java` |

The flow for a create command:

```
CreateProductCommand
        |
        v
ProductCommandHandler.createProduct()
        |--- saves Product to write DB (H2)
        |--- publishes ProductCreated to Kafka topic "product-created"
                        |
                        v
              ProductProjection.onProductCreated()
                        |--- saves ProductListing to read DB (same H2 in this demo)
```

## How to run

You need a local Kafka broker on `localhost:9092`. The simplest way:

```bash
docker run -d --name kafka \
  -p 9092:9092 \
  apache/kafka:3.8.0
```

Then from the repo root:

```bash
mvn -pl chapter-04-cqrs spring-boot:run
```

## Running the tests

The tests use `@EmbeddedKafka` — no external broker required:

```bash
mvn -pl chapter-04-cqrs test
```

Test classes:

| File | What it covers |
|---|---|
| `ProductCommandHandlerTest` | Unit — verifies writes and event publishing in isolation |
| `ProductProjectionTest` | Unit — verifies read model updates from each event type |
| `CqrsIntegrationTest` | Integration — full flow from command to read model via embedded Kafka |
