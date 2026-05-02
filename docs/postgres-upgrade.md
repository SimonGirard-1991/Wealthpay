# PostgreSQL Upgrade Runbook — Observability Hazards

This document tracks version-bump hazards in the observability stack: places
where a PostgreSQL major-version upgrade silently breaks a dashboard or
collector query without producing an obvious error signal. Each entry
describes the mechanism, the symptom, and the concrete fix recipe.

Current stack version: **PostgreSQL 18** (pinned in `docker-compose.local.yml`; cut over from PG16
on 2026-05-01 via `pg_upgrade --link` per [postgres-18-migration-plan.md](postgres-18-migration-plan.md)).
Both PG17 and PG18 hazards documented below have been applied — sections retained as historical
context and as a template for future major-version bumps (PG19 will introduce its own hazards).

---

## PG17 — `pg_stat_bgwriter` columns relocate to `pg_stat_checkpointer`

> **Status: Applied** — fix recipe wired in during the PG18 migration; see
> [postgres-18-migration-plan.md](postgres-18-migration-plan.md) Phase 2.6
> (`docker/prometheus/rules/db.rules.yml` and `docker/grafana/dashboards/db-server-health.json`
> updates) and the `--collector.stat_checkpointer` flag in `docker-compose.local.yml`.

### Mechanism

PostgreSQL 17 splits `pg_stat_bgwriter` and relocates the checkpoint-specific
columns to a new `pg_stat_checkpointer` view:

| Column (PG16 on `pg_stat_bgwriter`) | PG17 location                        |
|-------------------------------------|--------------------------------------|
| `checkpoints_timed`                 | `pg_stat_checkpointer.num_timed`     |
| `checkpoints_req`                   | `pg_stat_checkpointer.num_requested` |
| `checkpoint_write_time`             | `pg_stat_checkpointer.write_time`    |
| `checkpoint_sync_time`              | `pg_stat_checkpointer.sync_time`     |

postgres-exporter (currently pinned at `v0.19.1`) handles this with a
version-gated collector — but the relevant collector is **opt-in** and
**default off**.

### Symptom on upgrade

Without explicit action, immediately after bumping Postgres to PG17:

- The built-in `pg_stat_bgwriter` collector stops emitting the four checkpoint
  metrics, because the columns no longer exist on that view.
- postgres-exporter does **not** fail — the scrape succeeds, `up` stays green,
  and `pg_up` stays 1.
- The `pg_stat_bgwriter_checkpoints_*_total` and
  `pg_stat_bgwriter_checkpoint_{sync,write}_time_total` series freeze.
- Dashboard panels #12 and #13 in `db-server-health.json` go silently flat.
- Any alert rule that depends on checkpoint rate / time silently loses its
  signal without firing a "NoData" alert unless explicitly configured to.

### Fix recipe

In `docker-compose.local.yml`, under the `postgres-exporter` service's
`command:` block, add the opt-in collector flag:

```yaml
command:
  - "--collector.stat_statements"
  - "--collector.stat_statements.include_query"
  - "--collector.stat_checkpointer"   # Required from PG17 onward.
```

Then update the Grafana panels to use the new metric names. The expected
emitted names (confirm with `curl -s localhost:9187/metrics | grep checkpointer`
after enabling the collector) are:

| Old PromQL (PG16)                                 | New PromQL (PG17+)                               |
|---------------------------------------------------|--------------------------------------------------|
| `pg_stat_bgwriter_checkpoints_timed_total`        | `pg_stat_checkpointer_num_timed_total`           |
| `pg_stat_bgwriter_checkpoints_req_total`          | `pg_stat_checkpointer_num_requested_total`       |
| `pg_stat_bgwriter_checkpoint_write_time_total`    | `pg_stat_checkpointer_write_time_total`          |
| `pg_stat_bgwriter_checkpoint_sync_time_total`     | `pg_stat_checkpointer_sync_time_total`           |

Panels #12 and #13 in `docker/grafana/dashboards/db-server-health.json`
reference these by name and must be updated in the same change.

### References

- postgres-exporter issue #1060 (`checkpoints_timed` missing on PG17)
- PostgreSQL 17 release notes — new `pg_stat_checkpointer` view

---

## PG18 — `pg_stat_wal` columns relocate to `pg_stat_io`

> **Status: Applied** — fix recipe wired in during the PG18 migration; see
> [postgres-18-migration-plan.md](postgres-18-migration-plan.md) Phase 2.4
> (`docker/sql-exporter/wal-io.collector.yml` split into reduced `pg_stat_wal` query +
> `pg_stat_io_wal` aggregation query). Phase 5 (2026-05-02) on the live PG18 cluster surfaced
> and corrected the `SUM(...)` aggregation issue documented in step 2 below.

### Mechanism

PostgreSQL 18 removes WAL I/O columns from `pg_stat_wal` and relocates them
to `pg_stat_io` with `object = 'wal'`:

| Column (PG14–PG17 on `pg_stat_wal`) | PG18 location                                  |
|-------------------------------------|------------------------------------------------|
| `wal_write`                         | `pg_stat_io.writes` where `object='wal'`       |
| `wal_sync`                          | `pg_stat_io.fsyncs` where `object='wal'`       |
| `wal_write_time`                    | `pg_stat_io.write_time` where `object='wal'`   |
| `wal_sync_time`                     | `pg_stat_io.fsync_time` where `object='wal'`   |

The remaining `pg_stat_wal` columns (`wal_records`, `wal_fpi`, `wal_bytes`,
`wal_buffers_full`) stay on the view.

### Symptom on upgrade

PostgreSQL resolves column references at plan time, not at row-materialization
time. Immediately after bumping to PG18:

- The `pg_stat_wal` query in `docker/sql-exporter/wal-io.collector.yml` fails
  at parse time with `column "wal_write" does not exist`.
- sql-exporter's contract is all-or-nothing per collector query — the entire
  `pg_stat_wal` collector returns zero rows for that scrape.
- Net effect: **all 8 `pg_wal_stat_*` metrics freeze**, not just the 4 that
  moved. `pg_wal_stat_bytes_total`, `pg_wal_stat_buffers_full_total`,
  `pg_wal_stat_records_total`, `pg_wal_stat_fpi_total` all disappear even
  though their underlying columns still exist.
- Dashboard panels #8, #9, #10, #11 in `db-server-health.json` go silent.

### Fix recipe

Split the single `pg_stat_wal` query in
`docker/sql-exporter/wal-io.collector.yml` into two:

1. A reduced `pg_stat_wal` query selecting only the 4 remaining columns:

   ```yaml
   - query_name: pg_stat_wal
     query: |
       SELECT
         wal_records,
         wal_fpi,
         wal_bytes::double precision AS wal_bytes,
         wal_buffers_full
       FROM pg_stat_wal
   ```

2. An addition to the existing `pg_stat_io` query to surface the `object='wal'`
   rows as named metrics. The legacy `pg_stat_wal.{wal_write,wal_sync,wal_write_time,wal_sync_time}`
   columns were **cluster-wide totals**; on PG18 GA, `pg_stat_io` exposes them
   split by `(backend_type, object, context)`. The semantically faithful
   reconstruction is therefore `SUM(...)` across the whole `object='wal'`
   partition — narrowing to a single backend type understates writes by an
   order of magnitude (this stack: `walwriter`=23 writes vs cluster total=819
   at the same instant, with `standalone backend`, `client backend`,
   `checkpointer`, `autovacuum worker`, `startup`, `walsender` all contributing).
   Aggregating with `SUM` also collapses the per-row `(init, normal)` context
   split that PG18 GA emits — without aggregation, two rows arrive for
   labels-free metrics and Prometheus rejects with `"was collected before with
   the same name and label values"`. **Do not filter by `backend_type` here.**

   ```yaml
   - query_name: pg_stat_io_wal
     query: |
       SELECT
         SUM(writes)     AS wal_write,
         SUM(fsyncs)     AS wal_sync,
         SUM(write_time) AS wal_write_time,
         SUM(fsync_time) AS wal_sync_time
       FROM pg_stat_io
       WHERE object = 'wal'
   ```

   Then update the 4 affected metric definitions
   (`pg_wal_stat_write_total`, `pg_wal_stat_sync_total`,
   `pg_wal_stat_write_time_milliseconds_total`,
   `pg_wal_stat_sync_time_milliseconds_total`) to reference the new query via
   `query_ref: pg_stat_io_wal`.

3. Verify the per-row shape against live PG18 output before committing.
   Confirm with `SELECT backend_type, object, context, writes
   FROM pg_stat_io WHERE object = 'wal'` on a PG18 instance — on PG18.3
   this returns 28 rows (14 backend types × 2 contexts: `init` and `normal`).
   Earlier drafts of this recipe filtered to `backend_type='walwriter'`
   under the assumption that PG18 would attribute all WAL writes there;
   that filter (a) returns two rows due to the context split, breaking
   labels-free emission, and (b) understates cluster-wide writes by ~36×.
   Phase 5 (2026-05-02) on the live PG18 cluster surfaced both issues
   simultaneously; the `SUM(...)` form above corrects both.

4. Dashboard panels #8–#11 reference the same metric names and require no
   changes if the metric names are preserved (which the split above does).

### References

- PostgreSQL hackers: "Show WAL write and fsync stats in pg_stat_io"
- PostgreSQL 18 release notes (cumulative statistics system changes)

---

## Upgrade process (both versions)

These two hazards share a mitigation window — if you're bumping from PG16 to
PG17 you'll hit the first one; if you're bumping from PG17 to PG18 you'll
hit the second. In either case:

1. Bump the image tag in `docker-compose.local.yml`.
2. Apply the relevant fix recipe above before restarting the stack —
   dashboards will go silent between the DB coming up and the collector fix
   landing.
3. Verify the exporter output end-to-end before declaring done:
   - `curl -s localhost:9187/metrics | grep -E '<affected metric names>'`
     (postgres-exporter, for PG17)
   - `curl -s localhost:9399/metrics | grep pg_wal_stat` (sql-exporter, for PG18)
4. Spot-check the Grafana dashboard panels that depend on the affected
   metrics — a live-responsive panel is the authoritative proof the wiring
   works.
