# ADR-001: Event Sourcing for Account Aggregate

## Status

Accepted

## Context

The Account domain models monetary balances, reservations, and lifecycle
(open, active, closed).

This domain has strong consistency and auditability requirements:

- Monetary operations must preserve invariants (no overdraft, currency consistency)
- State changes must be fully traceable and replayable
- Concurrent updates must be detected and rejected safely

A traditional CRUD / state-based persistence model would make it harder to:

- audit all balance-affecting operations,
- reason about historical state,
- enforce optimistic concurrency in a precise and explicit way.

## Decision

We adopt an event-sourced domain model for the Account aggregate.

- The Account aggregate is the sole authority for enforcing domain invariants.
- State is derived exclusively by replaying domain events.
- Commands are handled by the aggregate, which decides and emits events.
- No direct state mutation occurs outside event application.

### Aggregate boundaries

- Account is the aggregate root.
- All balance-affecting operations (credit, debit, reserve, capture, cancel, close) are modeled as commands.
- Only the Account aggregate can emit AccountEvents.

### Persistence model

- Events are stored in an append-only event store.
- Each event carries:
    - accountId
    - version (monotonically increasing, per aggregate)
    - occurredAt
- The current state is reconstructed by replaying the event stream.

### Concurrency control

- Optimistic locking is enforced via the event store.
- `(account_id, version)` is unique.
- The expected version is supplied by the application service when appending events.
- Version conflicts are treated as concurrent modification errors.

## Consequences

### Positive

- Full audit trail of all balance changes
- Clear separation between decision (command handling) and state evolution (event application)
- Strong consistency guarantees within aggregate boundaries
- Explicit and deterministic concurrency handling
- Natural fit for future extensions (projections, read models, messaging)

### Negative

- Higher conceptual complexity compared to CRUD
- Requires careful discipline in aggregate design
- Rehydration cost grows with event history (mitigated with snapshot)

### Constraints and Invariants

- Aggregate state must only be derived from events.
- No command may bypass the aggregate to mutate state.
- Events are immutable once persisted.
- Event streams must start with an AccountOpened event.

## Alternatives Considered

### CRUD with state-based persistence

**Rejected:**

- Weak auditability
- Implicit state transitions
- Harder concurrency semantics
- Poor fit for financial domain requirements

### Event sourcing outside the domain (anemic model)

**Rejected:**

- Business invariants would be enforced outside the aggregate
- Higher risk of invariant leakage and duplication
