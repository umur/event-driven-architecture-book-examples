# Chapter 08 — Consumer Lag Monitoring

## What is consumer lag?

Consumer lag is the gap between the **latest offset** in a Kafka partition and
the **current offset** of a consumer group. A lag of zero means the consumer is
fully caught up. A growing lag means messages are arriving faster than the
consumer can process them — a warning sign that needs an alert.

```
partition head  ────────────────────────────────► offset 1000
                                          ▲
consumer position ──────────────────────►  offset 940
                                          │
                                     lag = 60
```

## How this example works

`SlowOrderConsumer` deliberately sleeps 200 ms for every message. That caps
its throughput at ~5 messages per second. Under any realistic load the lag
will grow.

`ConsumerLagMonitor` reads the `records-lag-max` metric from Spring Kafka's
`MessageListenerContainer.metrics()` map and publishes it as a Micrometer
`Gauge` named **`kafka.consumer.lag.current`**. Any Micrometer backend
(Prometheus, Datadog, CloudWatch …) will then scrape it automatically.

## The metric

| Attribute   | Value                                   |
|-------------|-----------------------------------------|
| Name        | `kafka.consumer.lag.current`            |
| Type        | Gauge                                   |
| Tags        | `container.id`, `group.id`              |
| Source      | `records-lag-max` from Kafka client JMX |

A value of `-1.0` means the consumer has not yet polled its first batch —
this is normal during the first few seconds after startup.

## What to alert on

| Condition                          | Severity | Action                         |
|-----------------------------------|----------|--------------------------------|
| lag > 1 000 for > 60 s            | Warning  | Scale out consumers            |
| lag > 10 000 for > 300 s          | Critical | Page on-call, check producer   |
| lag stuck at same value for > 5 m | Critical | Consumer may be stalled/crashed |

## Prometheus scrape config (example)

```yaml
scrape_configs:
  - job_name: 'eda-consumer-lag'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

## Running locally

Start Kafka:

```bash
docker run -p 9092:9092 apache/kafka:3.8.0
```

Run the application and publish some messages:

```bash
mvn -pl chapter-08-consumer-lag spring-boot:run

# in another terminal
kafka-producer-perf-test.sh \
  --topic orders \
  --num-records 10000 \
  --record-size 100 \
  --throughput 100 \
  --producer-props bootstrap.servers=localhost:9092
```

Inspect the metric:

```bash
curl -s http://localhost:8080/actuator/metrics/kafka.consumer.lag.current | jq .
```

Run the tests:

```bash
mvn -pl chapter-08-consumer-lag test
```
