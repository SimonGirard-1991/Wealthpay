# ADR-006: Snapshot strategy

## Status

Accepted

## Context

As mentioned in [ADR-001](./001-event-sourced-aggregate-model-for-account-domain.md), account aggregate state is derived
from events and is loaded through event rehydration and rehydration costs increase with event history.

As it is, a highly active account with millions of transactions, will degrade performance.

## Decision

We introduce a snapshot strategy to reduce rehydration costs.

- A snapshot threshold has been introduced and is configurable through environment variable
  `ACCOUNT_SNAPSHOT_THRESHOLD`.
- For a given account, every N events since the last snapshot, the snapshot is updated or created.
- Rehydration is done by replaying the event stream from the snapshot.

### Snapshot persistence model

- The snapshot is persisted in a table `account_snapshot`.
- This table contains all the fields of the account aggregate and the schema version of the snapshot, in case of future
  evolutions.
- Fields are stored in a JSONB column for convenience, just like the event store.

### Snapshot writing strategy

- After events are persisted, in the same transaction, when a threshold is reached, a snapshot is written.
- If the persistence of a snapshot fails because of bad serialization, nothing happens because a snapshot is not
  critical like events and is a performance optimization.
- A database exception makes the transaction rollback, and in this case, event persistence is rolled back.

### Snapshot reading strategy

- When events are loaded, we rely first on snapshot and then rehydration from it.
- If reading the snapshot fails, we fall back to rehydration from events, because the snapshot is not critical like
  events and is a performance optimization.

## Consequences

### Positive

- Rehydration costs are reduced and limited.
- No difference between very active accounts and inactive accounts in rehydration performance (memory and time).

### Negative

- A database error during snapshot persistence causes the entire transaction (including event persistence) to roll back.
- Complexity of managing snapshot schema evolution.

## Alternatives Considered

### Stay as it was

**Rejected:**

- Impossibility to manage big company accounts with millions of events.
- Differences of performance in rehydration between recent and old accounts.

### Time-based snapshot

**Rejected:**

- Rehydration costs remain unpredictable: a highly active account may accumulate thousands of events between snapshots,
  while an inactive one accumulates none.

### Async snapshot

**Deferred:**

- Would decouple snapshot persistence from event persistence, eliminating transaction rollback risk.
- Adds infrastructure complexity (message queue or async processing) not justified at current scale.
