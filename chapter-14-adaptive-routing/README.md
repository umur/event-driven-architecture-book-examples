# Chapter 14 — Adaptive Routing (Content-Based Router)

## What this module demonstrates

Content-based routing: an incoming event is inspected and forwarded to one of several downstream topics based on its payload. The routing decision is a pure function — no I/O, no Spring beans — which makes it trivially unit-testable.

---

## Routing rules

| Order total | Destination topic |
|---|---|
| >= $1000 | `orders-high-value` |
| >= $100 | `orders-premium` |
| < $100 | `orders-standard` |

---

## Why the routing predicate is a pure function

`OrderRoutingPredicate.determineDestination()` takes an `OrderPlaced` record and returns a `String`. It has no side effects and no dependencies. This design choice pays off immediately in testing:

```java
// No Spring context, no Kafka, runs in milliseconds.
assertThat(determineDestination(order("999.99"))).isEqualTo(TOPIC_PREMIUM);
assertThat(determineDestination(order("1000.00"))).isEqualTo(TOPIC_HIGH_VALUE);
```

`OrderRoutingPredicateTest` covers every boundary value. If the thresholds change, the test suite catches the regression before the code reaches Kafka.

The `OrderRouter` listener is kept deliberately thin: it calls the predicate, forwards to Kafka, and acknowledges. There is nothing in the router worth unit-testing in isolation — the interesting logic lives in the predicate.

---

## Extending with more routing rules

Add a new constant and a new branch in `OrderRoutingPredicate.determineDestination()`:

```java
public static final String TOPIC_INTERNATIONAL = "orders-international";

public static String determineDestination(OrderPlaced order) {
    if (order.customerId().startsWith("INTL-")) {
        return TOPIC_INTERNATIONAL;
    }
    if (order.total().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
        return TOPIC_HIGH_VALUE;
    }
    // ...
}
```

Then add a boundary-value test in `OrderRoutingPredicateTest`. The integration test does not need to change unless you want to verify the new route end-to-end.

---

## Test inventory

| File | Pattern | Infrastructure |
|---|---|---|
| `OrderRoutingPredicateTest` | Boundary-value unit test | None — pure Java |
| `ContentBasedRoutingIntegrationTest` | End-to-end routing verification | EmbeddedKafka + Awaitility |

---

## Running the tests

```bash
./mvnw test -pl chapter-14-adaptive-routing
```
