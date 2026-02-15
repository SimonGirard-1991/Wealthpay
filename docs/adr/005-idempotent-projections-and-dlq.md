# ADR-005: Idempotent projections and DLQ

## Status

Accepted

## Context

The account balance read model is updated by consuming domain events
from Kafka (see ADR-003). Delivery semantics are at-least-once:
the same event may be delivered more than once (consumer rebalance,
retry after transient failure, Debezium redelivery).

A non-idempotent projection would corrupt the read model — for example,
applying a `FundsCredited` event twice would double the displayed balance.

Additionally, not all consumption failures are equal:

- A transient failure (database unavailable) should be retried
  until the infrastructure recovers.
- A permanent failure (corrupted payload, version gap) will never
  succeed and must not block the partition.

## Decision

### Version-based idempotent projection

The `AccountBalanceReadModel` uses the event `version` (monotonically
increasing per aggregate) to guarantee idempotency:

1. **Duplicate detection**: if the event version is lower than the
   next expected version, the event is skipped (no-op).
2. **Gap detection**: if the event version is higher than the next
   expected version, an `IllegalStateException` is thrown. A gap
   indicates missing events and the projection cannot be safely
   advanced.
3. **Conditional upsert**: the database write uses
   `WHERE version < newVersion`. This is a second safety net —
   even in case of concurrent consumers or reprocessing, the row
   is only updated if the new version is strictly higher.

If all events in a batch are skipped (already applied), no database
write is performed.

### Error handling and Dead Letter Queue

The `KafkaErrorConfig` classifies exceptions into two categories:

| Category      | Behavior                                                           | Examples                                                                                                    |
|---------------|--------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| Non-retriable | Immediate DLQ publication, no retry                                | `IllegalStateException` (version gap), `IllegalArgumentException` (unsupported currency, malformed payload) |
| Retriable     | Exponential backoff (1s initial, x2 multiplier), unlimited retries | Database unavailable, transient Kafka errors                                                                |

We choose unlimited retries for retriable errors because event
processing is critical in a financial system: we prefer temporary
unavailability of the read model over silent data loss. Consistency
is prioritized over availability.

Non-retriable errors are sent to a DLQ topic for manual inspection
and potential replay after root-cause resolution.

## Consequences

### Positive

- Effective exactly-once projection despite at-least-once delivery.
- Version gaps are detected immediately, preventing silent read model
  corruption.
- Transient failures self-heal without manual intervention.
- DLQ preserves poison messages for investigation without blocking
  healthy event processing.

### Negative

- Unlimited retries can mask a persistent infrastructure issue
  (requires monitoring on consumer lag).
- Version gaps are treated as non-retriable, but could in rare cases
  be caused by a temporary reordering. This is mitigated by Kafka's
  per-partition ordering guarantee (same aggregate = same partition).
- DLQ replay is currently manual; no automated tooling exists.

## Alternatives Considered

### Reject duplicates with optimistic locking exception

Previous behavior: an exception was thrown when a duplicate version
was projected, relying on optimistic locking to prevent inconsistency.

Rejected because:

- Not idempotent: duplicates caused failures instead of being
  silently absorbed.
- Broke effective exactly-once semantics under at-least-once delivery.
- Generated unnecessary errors and retries for a normal operating
  condition (redelivery).

### Finite retry with DLQ for all errors

Rejected because:

- A transient database outage would exhaust retries and send critical
  events to the DLQ.
- Recovery from infrastructure failures could require the manual replay
  of a significant number of events.
- Unacceptable risk of data loss in a financial context.
