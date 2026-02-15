# ADR-0003: Transactional Outbox Pattern with CDC

## Status

Accepted

## Context

Initially, write model (event store) and read model (projection table)
were updated in the same database transaction.

This design guaranteed strong consistency but tightly coupled the
read model to the write path, preventing independent scaling,
replay capability, and operational isolation.

We want to:

- Decouple write model and read model processing.
- Enable replay of projections from the event stream.
- Avoid dual-write inconsistencies.
- Provide at-least-once delivery semantics with idempotent processing.

## Decision

We adopt the Transactional Outbox pattern using Change Data Capture (CDC).

1. A dedicated `account.outbox` table is introduced in PostgreSQL.
   Business events are written atomically in the same transaction
   as the aggregate state change.

2. PostgreSQL is configured with `wal_level=logical` to enable
   logical replication.

3. Debezium (Postgres connector) is configured to stream changes
   from the outbox table to Kafka.

4. Events are published to the `wealthpay.AccountEvent` topic,
   partitioned by `accountId` to guarantee per-aggregate ordering.

5. The outbox schema is intentionally minimal.
   Columns related to polling (`status`, `publish_attempts`, `last_error`,
   `published_at`) are removed (V6 migration): Debezium owns delivery
   semantics entirely. Outbox rows are immutable once inserted (no
   application-level UPDATE). Periodic DELETE for cleanup is expected,
   since Debezium captures events from the WAL, not from the table itself.

6. The read model projection consumes from Kafka and applies events
   idempotently (see ADR-005).

## Delivery Guarantees

- Kafka provides per-partition ordering.
- Delivery semantics: at-least-once.
- Effective-once behavior achieved via idempotent projections.
- No global ordering guarantee across aggregates.

## Consequences

### Positive

- Decoupled write and read models.
- Per-aggregate total ordering guaranteed by Kafka partitioning.
- Replay capability from Kafka.
- Elimination of non-atomic DB + Kafka dual-write inconsistencies.
- Independent scalability of projection consumers.
- Fault isolation between write path and projection processing.

### Constraints and Invariants

- Outbox writes must occur in the same database transaction as event store appends.
- Outbox rows are immutable: no application-level UPDATE. DELETE is permitted
  for housekeeping (Debezium reads the WAL, not the table).
- `aggregate_id` is used as the Kafka message key, ensuring per-aggregate
  partition affinity and total ordering.
- Debezium is the sole mechanism for outbox consumption; the application
  never reads from the outbox table.

### Negative

- Increased operational complexity (Kafka, Debezium, Schema Registry).
- At-least-once delivery implies duplicates must be handled.
- Requires careful monitoring (consumer lag, DLQ, replication health).
- Infrastructure setup cost (3 brokers, replication configuration).

## Alternatives Considered

### Synchronous projection in the same transaction

Rejected because:

- Tight coupling between write and read models.
- No replay capability.
- No independent scaling.

### Transactional Outbox with application-level polling

Rejected because:

- Increased database load due to periodic scans.
- Higher latency compared to CDC.
- More custom code to maintain.
- CDC (Debezium) provides a more standardized and scalable approach.
