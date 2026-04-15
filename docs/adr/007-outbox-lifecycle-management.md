# ADR-007: Outbox Lifecycle Management

## Status

Accepted

## Context

[ADR-003](./003-transactional-outbox-pattern-with-cdc.md) introduced the transactional outbox pattern:
every account operation atomically persists an event to the event store and a corresponding row to
`account.outbox`. Debezium captures these rows from the PostgreSQL WAL and routes them to Kafka.
Once a row has been written to the WAL, the outbox table copy serves no further purpose — in
steady state Debezium streams exclusively from the WAL, not from the table. (On first connector
creation or offset loss, Debezium's default `initial` snapshot mode does read the table, but the
outbox is not a source of truth: re-published rows are deduplicated by idempotent consumers — see
[ADR-005](./005-idempotent-projections-and-dlq.md).)

Without cleanup the outbox table grows monotonically: unbounded storage, index bloat, and
increasing `VACUUM` pressure. ADR-003 already anticipated this ("DELETE is permitted for
housekeeping") but left the strategy open. This ADR closes that loop.

## Decision

We adopt range partitioning by day combined with pg_cron-driven partition drops as the outbox
cleanup strategy.

### Outbox Partitioning

The `outbox` table is range-partitioned on `occurred_at` with one partition per day
(`outbox_YYYY_MM_DD`).

- **No default partition.** If partition pre-creation fails, inserts fail immediately with an error
  rather than silently routing into a catch-all partition that would defeat cleanup. This is a
  deliberate fail-fast choice: a missed partition is a loud, immediately actionable signal.
- **`publish_via_partition_root = true`** on the Debezium publication. Without this setting,
  PostgreSQL emits WAL changes under the child partition's identity (`outbox_2026_04_15`), which
  does not match Debezium's `table.include.list` filter (`account.outbox`). Setting this flag makes
  all child-partition changes appear as if they originated from the parent table, keeping the CDC
  pipeline transparent to partitioning.
- **Publication scoped to `account.outbox` only.** An earlier `FOR ALL TABLES` publication also
  captured `outbox_cleanup_log`, which has no primary key. PostgreSQL requires a replica identity
  for published UPDATEs, so the publication was narrowed to only the outbox table.
- The partition key (`occurred_at`) must be part of every unique constraint, so the uniqueness
  constraint becomes `UNIQUE(event_id, occurred_at)`.

### pg_cron Partition Lifecycle

A PL/pgSQL function `account.manage_outbox_partitions(retention_days)` handles both sides of the
lifecycle:

1. **Pre-creates** partitions for today + 7 days ahead (idempotent, handles concurrent execution
   via `EXCEPTION WHEN duplicate_table`).
2. **Drops** partitions older than `retention_days` (default 3).

This function is scheduled via pg_cron at 03:00 UTC daily.

The database owns the partitions, so the database owns the lifecycle. Key benefits of this
approach:

- Retention is configured as a PL/pgSQL `DEFAULT` parameter — single source of truth, no
  application redeployment needed to change it.
- The cron schedule lives in `cron.job`, not in application configuration.
- The function is idempotent, so concurrent execution (e.g. multiple pg_cron nodes) is safe.

### Failure Tracking

The function uses a top-level `EXCEPTION WHEN OTHERS` block. On failure, it catches the exception
and inserts a `failure` row into `outbox_cleanup_log` instead of re-raising. This is a deliberate
choice: if the function re-raised, pg_cron's outer transaction would roll back the entire
statement, including the failure log `INSERT`, defeating the purpose of failure tracking.

The `outbox_cleanup_log` table records per-run:

- `status` (`success` / `failure`), `started_at`, `completed_at` (using `clock_timestamp()` for
  wall-clock accuracy), `error_message`
- `partitions_created`, `partitions_dropped`, `remaining_partitions`

### Observability

The application **never executes** cleanup in production — it only observes the results.
`OutboxCleanupObserver` polls `outbox_cleanup_log` on a fixed delay (default 5 minutes) and
exposes two Micrometer gauges:

- `outbox.cleanup.last_run.seconds` — epoch timestamp of the last successful run.
- `outbox.cleanup.last_status` — 1 for success, 0 for failure (defaults to 0 when the table is
  empty, so "never ran" does not look healthy).

`OutboxMetrics` exposes a third gauge:

- `outbox.row_count.estimate` — estimated total rows via `pg_class.reltuples` (no sequential scan).

Three Prometheus alerts form a layered detection strategy:

- **OutboxCleanupFailed** — last cleanup status is `failure` (immediate signal).
- **OutboxCleanupStale** — no successful cleanup in over 36 hours (detects silent pg_cron
  failures, e.g., extension disabled, cron daemon stopped).
- **OutboxTableGrowing** — estimated row count exceeds 500k (catch-all safety net if both cleanup
  and alerting are broken).

### Spring Fallback Scheduler

`OutboxCleanupFallbackScheduler` exists for environments without pg_cron (local development, CI).
It is **disabled by default** (`outbox.cleanup.spring-execution.enabled=true` to activate) and
calls the same `manage_outbox_partitions()` function via jOOQ-generated routine bindings.

In multi-replica environments, the function's idempotency makes concurrent execution safe but
wasteful. A distributed lock (e.g., ShedLock) would be advisable if this scheduler were enabled in
such environments.

## Consequences

### Positive

- **O(1) cleanup** via `DROP TABLE` on expired partitions — no sequential scan, no dead tuples,
  no `VACUUM` pressure.
- **Bounded storage growth** — outbox retains at most `retention_days + 7` days of data.
- **Operational visibility** — cleanup failures are persisted in the database and surfaced through
  Prometheus alerts at three levels of severity.
- **No application-layer involvement** in production cleanup — the database is self-managing.

### Negative

- **Partitioning complexity** — partition naming convention must be consistent, pre-creation must
  stay ahead of inserts, and `publish_via_partition_root` must be set on the publication.
- **pg_cron dependency** — must be installed and enabled on the PostgreSQL instance. Managed
  database services (RDS, Cloud SQL) support it, but it is an additional operational requirement.
- **Custom observability** — the observer/polling pattern adds application code and Prometheus
  rules that must be maintained alongside the PL/pgSQL function.

## Alternatives Considered

### Application-level batch deletion (Spring `@Scheduled` + `DELETE WHERE`)

**Rejected:**

- O(N) delete generates dead tuples proportional to table size, requiring aggressive `VACUUM`.
- Index maintenance on each deleted row.
- Row-level locking contention with concurrent inserts during cleanup.

### `TRUNCATE` outbox table

**Rejected:**

- `TRUNCATE` acquires an `ACCESS EXCLUSIVE` lock on the entire table, blocking all concurrent
  inserts. Unacceptable for a table on the write-path critical path.

### Per-event deletion after Kafka consumption

**Rejected:**

- Doubles WAL volume: every outbox row generates one `INSERT` WAL entry (captured by Debezium)
  plus one `DELETE` WAL entry (also captured by Debezium, adding unnecessary processing).
- Doubles Debezium's work since it reads the full WAL stream.
- Constant write pressure on the database rather than a single daily `DROP TABLE`.
