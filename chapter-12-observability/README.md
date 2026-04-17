# Chapter 12 — Observability with OpenTelemetry

## What this module demonstrates

A distributed system is only as observable as the worst-instrumented service
in a request path. When an order flows from an HTTP handler through Kafka into
a payment service, you need a single thread you can pull on to see everything
that happened — across process boundaries, across the network, across time.

That thread is the **trace ID**.

OpenTelemetry standardizes how trace context crosses boundaries. Spring Boot
auto-configures the W3C Trace Context propagation format when
`micrometer-tracing-bridge-otel` is on the classpath. Every `KafkaTemplate.send`
call made while a span is active automatically injects a `traceparent` header
into the outgoing Kafka record. Every `@KafkaListener` method called while a
record with a `traceparent` header is being processed automatically restores
that context before your code runs.

## The traceparent header

```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
             ^^ ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^^ ^^
             version  traceId (128-bit hex)       spanId (64-bit)  flags
```

The trace ID is the same across the entire request chain regardless of how
many services or Kafka hops are involved.

## Architecture

```
OrderEventPublisher          PaymentEventHandler
  [tracer.nextSpan()]          [@KafkaListener]
      |                              |
  kafkaTemplate.send()    ←  traceparent header  ←  Kafka record
      |                              |
  Spring Kafka injects          Spring Kafka extracts
  traceparent header            traceparent header
                                tracer.currentSpan() is non-null
                                and shares the producer traceId
```

## Module layout

```
src/main/java/com/umurinan/eda/ch12/
  ObservabilityApplication     Spring Boot entry point
  OrderEventPublisher          creates span, publishes event
  PaymentEventHandler          consumes event, logs traceId
src/test/java/com/umurinan/eda/ch12/
  TracePropagationTest         asserts producer and consumer share traceId
  TraceHeaderTest              asserts traceparent header is physically present
docker-compose.yml             Kafka (KRaft) + Jaeger all-in-one
```

## Running the tests

```bash
mvn test -pl chapter-12-observability
```

## Running with Jaeger

```bash
# Start Kafka and Jaeger
docker compose up -d

# Run the application
mvn spring-boot:run -pl chapter-12-observability
```

Open Jaeger UI at http://localhost:16686.  Select service
`chapter-12-observability` and search for traces. Each trace will show the
producer span (`order.placed.publish`) linked to the consumer span
(`payment-service process`) with the same trace ID.

## Key dependencies

| Dependency | Role |
|---|---|
| `micrometer-tracing-bridge-otel` | Translates Micrometer Tracer API calls to OpenTelemetry SDK calls |
| `opentelemetry-exporter-otlp` | Exports completed spans to any OTLP-compatible backend (Jaeger, Grafana Tempo, Honeycomb, etc.) |
| `spring-boot-starter-actuator` | Exposes `/actuator/health`, `/actuator/metrics`, enables Micrometer auto-configuration |

## Sampling

`management.tracing.sampling.probability=1.0` records every trace. In
production lower this to `0.1` (10 %) or use a head-based sampler that
always records error traces regardless of the rate.

## Extending to multiple services

Deploy `OrderEventPublisher` and `PaymentEventHandler` as separate Spring Boot
applications. As long as both have `micrometer-tracing-bridge-otel` on the
classpath and point to the same OTLP endpoint, traces will stitch together
automatically — no code changes required.
