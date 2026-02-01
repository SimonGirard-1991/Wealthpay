# ADR-0002: Aggregate mutation in command handlers

## Status

Accepted

## Context

In our event-sourced Account aggregate, command handlers (`handle()` methods)
need to produce events. When multiple commands are executed sequentially in a
single service call (e.g., credit then reserve), subsequent commands need to
see the state changes from previous commands.

Two approaches exist:

1. **Pure handlers** — `handle()` returns events without mutating state.
   Requires "uncommitted events" tracking or rehydration between commands.
2. **Mutating handlers** — `handle()` calls `apply()` internally, mutating
   the aggregate before returning events.

## Decision

We adopt **mutating handlers**: each `handle()` method applies its produced
event to the aggregate state before returning.

```java
public List<AccountEvent> handle(CreditAccount cmd, Instant occurredAt) {
    // validations using current state...
    FundsCredited event = new FundsCredited(..., this.version + 1, ...);
    apply(event);  // state mutation
    return List.of(event);
}
```

## Consequences

### Positive

- Sequential commands in the same unit of work naturally observe prior decisions
- Simpler code than tracking uncommitted events
- In-memory version advances with applied events (this.version follows the last applied event)

### Negative

- The aggregate instance can temporarily reach a non-persisted state (it includes uncommitted events).
  If event persistence fails, the instance must be discarded (or rebuilt) to avoid diverging from the event store.
- Command idempotence is command-specific:
     -	Some commands may be implemented as no-ops when repeated (e.g., reservation operations keyed by reservationId)
     -	Others (e.g., credit/debit) are not idempotent unless we explicitly enforce it (e.g., via transactionId de-duplication)

### Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Same command is handled twice without idempotence guarantees  | Idempotent command identifiers where required, code review, integration tests |
| Coupling with application layer | Explicit helper method documents the contract |

## Alternatives Considered

### Uncommitted events pattern

Track uncommitted events internally, allowing the aggregate to distinguish
between persisted and pending state. This pattern offers better separation of
concerns but adds complexity.

Not adopted now: current use cases don't require it. May revisit if we need
aggregate reuse across multiple command batches or saga orchestration.

### Return tuple (events, previousVersion)

Rejected: verbose, non-idiomatic Java.
