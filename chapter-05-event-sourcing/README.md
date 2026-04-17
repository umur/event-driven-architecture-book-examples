# Chapter 05 — Event Sourcing

## What this demonstrates

Traditional persistence stores the *current state* of an entity by overwriting the previous row.
Event sourcing inverts that: the system stores *what happened*, not what is. Every state change is
recorded as an immutable domain event appended to the event store. To reconstruct an aggregate,
you replay its history from the first event to the last.

This module shows the core mechanics with no frameworks or special libraries — just plain Java,
Spring JDBC, and Kafka.

## Key concepts

**Event store** — an append-only log of domain events keyed by aggregate ID and sequence number.
A unique constraint on `(aggregate_id, sequence_number)` provides optimistic concurrency: if two
writers race to append sequence 3, only one will succeed.

**Aggregate rehydration** — `OrderAggregate.rehydrate(orderId, events)` creates a blank aggregate
and replays every stored event in order. The result is identical to the aggregate you would have
obtained by applying each command individually.

**State as a side-effect** — the aggregate never writes to a database directly. All writes go
through `EventStore.append(...)`. The current state is always *derived* from the event log.

## Module structure

```
src/main/java/com/umurinan/eda/ch05/
  domain/
    OrderAggregate.java          -- aggregate; apply() + rehydrate()
    OrderStatus.java             -- PENDING | PLACED | CANCELLED
    events/
      OrderEvent.java            -- sealed interface
      OrderPlacedEvent.java      -- record
      OrderCancelledEvent.java   -- record
  store/
    EventStore.java              -- append-only interface
    JdbcEventStore.java          -- JDBC + Jackson implementation
    EventSerializationException.java
  commands/
    PlaceOrderCommand.java
    CancelOrderCommand.java
  OrderCommandHandler.java       -- wires commands -> event store -> Kafka
  EventSourcingApplication.java
src/main/resources/
  schema.sql                     -- order_events table with unique constraint
  application.yml
```

## How to run

### Prerequisites

- Java 21
- Docker (for Kafka)

### Start Kafka

```bash
docker run -d --name kafka \
  -p 9092:9092 \
  -e KAFKA_ENABLE_KRAFT=yes \
  -e KAFKA_CFG_PROCESS_ROLES=broker,controller \
  -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CFG_NODE_ID=1 \
  bitnami/kafka:latest
```

### Run the application

```bash
cd chapter-05-event-sourcing
mvn spring-boot:run
```

### Run the tests

```bash
mvn test
```

The `EventStoreTest` uses an in-memory H2 database — no Kafka required.
The `OrderAggregateTest` and `OrderCommandHandlerTest` are pure unit tests with zero I/O.

## What happens when you place an order

1. `OrderCommandHandler.placeOrder(command)` creates an `OrderPlacedEvent`.
2. The event is appended to `order_events` with sequence_number=0.
3. The event is published to the `order-placed` Kafka topic.
4. To cancel, `cancelOrder(command)` loads all events, rehydrates the aggregate (validating
   that it is in PLACED status), appends `OrderCancelledEvent` at sequence_number=1,
   and publishes to `order-cancelled`.

To inspect the event store at runtime, open the H2 console at `http://localhost:8080/h2-console`
with JDBC URL `jdbc:h2:mem:event-store`.
