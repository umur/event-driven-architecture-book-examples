# Chapter 06 — CQRS + Event Sourcing Combined

## What this demonstrates

This module combines two patterns that belong together but are often taught separately.

**Event sourcing** provides the write model: every state change is stored as an event, never as a
mutated row. There is no "orders" table holding the current state — only `order_events`.

**CQRS** (Command Query Responsibility Segregation) keeps the read side separate. Domain events
flow to Kafka. Three independent consumer groups each maintain their own read model, shaped
exactly for the queries they serve. None of them share a table or coordinate with each other.

## The single-topic, multiple-consumer-group pattern

All events for the `order-events` topic are published to one place. Each consumer group reads
the topic independently at its own pace:

| Consumer group           | Read model table          | Purpose                              |
|--------------------------|---------------------------|--------------------------------------|
| `order-detail-service`   | `order_details`           | Full detail view of a single order   |
| `customer-history-service` | `customer_order_history` | All orders placed by a customer      |
| `analytics-service`      | `daily_order_summary`     | Aggregated revenue counts per day    |

Because Kafka tracks offsets per consumer group, a slow analytics projection does not block the
fast detail projection. Each group replays from its own committed offset.

## Heterogeneous events on one topic

A single topic carries multiple event types (`ORDER_PLACED`, `ORDER_SHIPPED`). Each projection
receives raw JSON, reads the `eventType` discriminator field, deserializes the payload to the
correct record class, and routes to its handler method. This avoids the complexity of
per-type topics while keeping each projection's logic self-contained.

## Module structure

```
src/main/java/com/umurinan/eda/ch06/
  events/
    OrderPlacedEvent.java
    OrderShippedEvent.java
    OrderEventType.java
  readmodel/
    OrderDetail.java                    -- JPA entity; PK = orderId
    OrderDetailRepository.java
    CustomerOrderHistory.java           -- JPA entity; auto-increment PK
    CustomerOrderHistoryRepository.java -- findByCustomerId(...)
    DailyOrderSummary.java              -- JPA entity; PK = summaryDate
    DailyOrderSummaryRepository.java
  projection/
    OrderDetailProjection.java          -- group: order-detail-service
    CustomerOrderHistoryProjection.java -- group: customer-history-service
    OrderAnalyticsProjection.java       -- group: analytics-service
  config/
    KafkaConfig.java                    -- topic, factory, MANUAL ACK
  CqrsEventSourcingApplication.java
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
cd chapter-06-cqrs-event-sourcing
mvn spring-boot:run
```

### Run the tests

```bash
mvn test
```

All three integration tests use `@EmbeddedKafka` — no external Kafka required.
`@DirtiesContext` on each test class ensures the embedded broker is reset between test runs so
consumer group offsets do not bleed across tests.

## Publishing a test event manually

With the application running against a live Kafka, produce a JSON message to `order-events`:

```json
{
  "eventType": "ORDER_PLACED",
  "payload": {
    "orderId": "order-123",
    "customerId": "cust-456",
    "total": 199.99,
    "occurredAt": "2026-03-30T10:00:00Z"
  }
}
```

All three projections will pick it up within milliseconds and update their respective tables.
Query the H2 console at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:cqrs-es-demo`)
to inspect the read models.
