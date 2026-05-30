# Account — Runtime Architecture

**Audience:** engineers and operators working on or running the service.
**Scope:** *how* an account command travels from an HTTP request to durable
storage and into the read model — transactions, messaging, CDC, and the
consistency guarantees that result. For the domain rules themselves, see
[business-flows.md](./business-flows.md).

> This documents the **account** bounded context. The decision records under
> [`../adr/`](../adr/) carry the *why* behind each choice; this page is the
> *map* that ties them together.

---

## Architectural style

The account context is an **event-sourced aggregate** behind a **CQRS** split:

- **Writes** go through the `Account` aggregate. Each command produces domain
  events that are the source of truth, appended to `event_store`
  ([ADR-001](../adr/001-event-sourced-aggregate-model-for-account-domain.md),
  [ADR-002](../adr/002-aggregate-mutation-in-command-handlers.md)).
- **Reads** are served from a denormalised projection, `account_balance_view`,
  never by replaying events.
- Write and read are bridged by the **Transactional Outbox + CDC** pattern: the
  same transaction that appends an event also writes an outbox row; Debezium
  streams that row to Kafka; a consumer projects it into the read model
  ([ADR-003](../adr/003-transactional-outbox-pattern-with-cdc.md)).

Layering is hexagonal: the domain has no framework dependencies, the
application layer orchestrates use cases, and infrastructure adapters
(`web`, `db`, `consumer`) talk to the outside world only through application
ports.

## Components at a glance

![Components](diagrams/components.png)

The dotted arrow to the aggregate is deliberate: the aggregate is called
in-process, returns events, and performs **no I/O**. The application service is
what persists — that is what keeps the domain framework-free.

## Write path

![Write path](diagrams/write-path.png)

### The transaction boundary

Everything inside the shaded box commits or rolls back **atomically** in one
database transaction:

1. **Idempotency check** — `register(Transaction-Id)`. If this key was already
   processed, the call short-circuits to `NO_EFFECT` and no new event is
   written.
2. **Load** the aggregate (latest snapshot + events appended since,
   [ADR-006](../adr/006-snapshot-strategy.md)).
3. **Handle** the command — the aggregate validates invariants and returns
   events. No I/O happens here.
4. **Append** the event(s) to `event_store` under an **optimistic version
   check**, and write the matching **outbox** row — same transaction.

Because the event and the outbox row are written together, there is no window
in which an event is durable but unpublished, or vice versa
([ADR-003](../adr/003-transactional-outbox-pattern-with-cdc.md)).

### Eventual consistency — read-your-writes caveat

> The write transaction acknowledges to the client **before** the read model is
> updated. The projection happens asynchronously (Debezium → Kafka →
> projector). A `GET /accounts/{id}` issued immediately after a successful
> write **may return the pre-write balance.**

This is the one behaviour every caller must understand. A UI that needs to show
the result of an action should use the value it already knows (the command
result), not an immediate re-read, until the projection catches up. End-to-end
lag is normally sub-second but is not zero and is not bounded by the write.

## Read path & projection reliability

Delivery from Kafka to the projector is **at-least-once** — the same event can
arrive more than once (consumer rebalance, retry, Debezium redelivery). The
projection is made effectively exactly-once by **version-based idempotency**
([ADR-005](../adr/005-idempotent-projections-and-dlq.md)):

- Event `version` **lower** than expected → already applied → skipped.
- Event `version` **higher** than expected → a gap → `IllegalStateException`
  (non-retriable, sent to DLQ — a gap must never be silently advanced).
- The DB write is a conditional upsert (`WHERE version < new`) as a second
  safety net.

Failure handling splits two ways:

| Failure | Treatment |
|---|---|
| Transient (DB down, transient Kafka) | Exponential backoff, unlimited retries — we prefer a stale read model over data loss |
| Permanent (version gap, malformed payload, unsupported currency) | Straight to the DLQ for inspection / replay |

Kafka's per-partition ordering (same aggregate → same partition) is what makes
the version sequence meaningful.

## Idempotency & concurrency

| Concern | Mechanism | Where |
|---|---|---|
| Duplicate deposit / withdrawal / reservation | `Transaction-Id` header → `processed_transactions` | application service |
| Duplicate capture / cancel | reservation phase → `processed_reservations` | application service |
| Concurrent writes to one account | optimistic version check on append | event store |
| Duplicate event delivery to read model | event `version` idempotency + conditional upsert | consumer / projector |

Idempotency is an **application + infrastructure** concern, not a domain
invariant — the aggregate itself holds no dedup state.

> **Ordering caveat:** deposits, withdrawals, and reservations resolve
> idempotency *before* loading the aggregate (a known key short-circuits to
> `NO_EFFECT`) — this is the pre-flight step in the write-path diagram. Capture
> and cancel differ: the aggregate reports no-effect first, then the reservation
> phase is checked. They do **not** follow the diagram's ordering.

## Endpoint reference

| Operation | Method & path | Idempotency key | Success |
|---|---|---|---|
| Open account | `POST /accounts` | — | `201` + `accountId` |
| Get account | `GET /accounts/{id}` | — | `200` + balance / reserved / status |
| Deposit | `POST /accounts/{id}/deposits` | `Transaction-Id` header | `200` + `COMMITTED` \| `NO_EFFECT` |
| Withdraw | `POST /accounts/{id}/withdrawals` | `Transaction-Id` header | `200` |
| Reserve | `POST /accounts/{id}/reservations` | `Transaction-Id` header | `200` + `reservationId` |
| Capture | `POST /accounts/{id}/reservations/{reservationId}/capture` | `reservationId` (path) | `200` |
| Cancel | `POST /accounts/{id}/reservations/{reservationId}/cancel` | `reservationId` (path) | `200` |
| Close account | `POST /accounts/{id}/close` | — (idempotent by state) | `200` + `COMMITTED` \| `NO_EFFECT` |

The read model (`AccountResponse`) exposes `balanceAmount` and `reservedAmount`;
**available** is derived as `balanceAmount − reservedAmount`.

---

> The diagrams are hand-authored SVGs in [`diagrams/`](./diagrams/); run
> [`render.sh`](./diagrams/render.sh) to re-rasterize them to PNG after editing.

## See also

- [business-flows.md](./business-flows.md) — domain rules and lifecycle.
- [ADR-003](../adr/003-transactional-outbox-pattern-with-cdc.md) — outbox + CDC.
- [ADR-005](../adr/005-idempotent-projections-and-dlq.md) — idempotent projection + DLQ.
- [ADR-006](../adr/006-snapshot-strategy.md) — snapshots.
