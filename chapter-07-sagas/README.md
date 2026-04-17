# Chapter 07 — SAGAs (Orchestration)

## What this demonstrates

An **orchestration SAGA** is a long-running transaction coordinator. A single
component — the orchestrator — holds the entire workflow in its head, issues
commands to participant services one step at a time, and compensates if anything
goes wrong. Participants are dumb: they do their job and reply. The intelligence
lives in the orchestrator.

This contrasts with a **choreography SAGA** where each participant reacts to
events and decides its own next step. Orchestration is easier to reason about
and debug; choreography scales better but becomes hard to trace.

---

## State machine

```
PENDING
  └─► PAYMENT_PROCESSING ──(PaymentReply success)──► INVENTORY_RESERVING
              │                                              │
       (PaymentReply fail)                        (InventoryReply success)
              │                                              │
              ▼                                              ▼
           FAILED                                 SHIPMENT_SCHEDULING
                                                             │
                                         (InventoryReply fail)
                                                             │
                                                             ▼
                                                       COMPENSATING ──► FAILED
                                                             │
                                                   (ShipmentReply success)
                                                             │
                                                             ▼
                                                        COMPLETED
```

### States

| State                | Meaning                                                    |
|---------------------|------------------------------------------------------------|
| PENDING             | Saga created, not yet started (reserved for future use)   |
| PAYMENT_PROCESSING  | ProcessPaymentCommand sent, waiting for reply             |
| INVENTORY_RESERVING | ReserveInventoryCommand sent, waiting for reply            |
| SHIPMENT_SCHEDULING | ScheduleShipmentCommand sent, waiting for reply           |
| COMPENSATING        | InventoryReply failed; RefundPaymentCommand sent          |
| COMPLETED           | All three steps succeeded                                  |
| FAILED              | Terminal failure — no further action taken by orchestrator |

---

## Topics

| Topic               | Direction       | Payload                    |
|--------------------|-----------------|----------------------------|
| payment-commands   | orchestrator → payment   | ProcessPaymentCommand / RefundPaymentCommand |
| payment-replies    | payment → orchestrator   | PaymentReply               |
| inventory-commands | orchestrator → inventory | ReserveInventoryCommand    |
| inventory-replies  | inventory → orchestrator | InventoryReply             |
| shipment-commands  | orchestrator → shipment  | ScheduleShipmentCommand    |
| shipment-replies   | shipment → orchestrator  | ShipmentReply              |

---

## Timeout detection

`SagaTimeoutHandler` runs every 5 seconds. Any saga that has been in
`PAYMENT_PROCESSING` for more than 30 seconds is marked `FAILED`. This guards
against a payment service that crashes without ever sending a reply.

Scheduling is disabled in tests via `scheduling.enabled=false` so tests drive
state transitions explicitly rather than racing against the timer.

---

## Running locally

Start Kafka (adjust the bootstrap address in `application.yml` if needed):

```bash
docker run -p 9092:9092 apache/kafka:3.8.0
```

Run the application:

```bash
mvn -pl chapter-07-sagas spring-boot:run
```

Run the tests:

```bash
mvn -pl chapter-07-sagas test
```

The integration test uses `@EmbeddedKafka` so no external broker is needed for
`mvn test`.
