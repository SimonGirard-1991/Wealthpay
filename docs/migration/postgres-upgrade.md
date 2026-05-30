# PostgreSQL Upgrade — Observability Hazards Checklist

When bumping the PostgreSQL major version, work through this checklist before
declaring the upgrade done. Each section describes a *class* of hazard, the
detection recipe, and the fix shape. The named PG version on each section is
the example that surfaced the pattern — a future major-version bump will
likely surface a fresh instance of the same class.

Current stack version: **PostgreSQL 18** (pinned in `docker-compose.local.yml`).

## Why this document is shaped as hazard classes, not version diffs

The failure mode that motivated this doc is not "PG17 renamed a column." It
is **silent metric freeze**: a query that resolves at parse time stops
returning rows after a major-version bump, the scrape continues to succeed,
`up` stays green, and the only signal is a flat panel on a dashboard nobody
is watching. Any major-version bump can produce a fresh instance of this.
Categorising the hazards by class — not by version — keeps the recipe useful
for the next bump.

---

## Hazard class 1 — `pg_stat_*` view splits and column relocations

### Mechanism

PostgreSQL periodically reorganises its cumulative-statistics catalog views.
A column that lived on view A in version N may be relocated to view B in
version N+1, sometimes with a renamed column, sometimes split across new
label dimensions.

PostgreSQL resolves column references at **plan time**, not at row
materialisation time. Any collector query that names a column moved out from
under it fails at parse time the moment the new server version is in place.

### Symptom

Two distinct failure surfaces depending on which exporter the collector
lives in:

- **postgres-exporter built-in collector** — silently stops emitting the
  affected metrics. Scrape continues, `pg_up` stays 1, no error log. The
  affected series go flat on the dashboard.
- **sql-exporter custom collector** — the **whole collector query** returns
  zero rows (sql-exporter contract is all-or-nothing per query). Every
  metric defined under that query disappears, even ones whose underlying
  columns still exist. See hazard class 2 for the consequences.

### Detection

Before the bump, on a development copy of the new version:

```bash
# postgres-exporter side
curl -s localhost:9187/metrics | grep -E '<expected metric prefixes>' | wc -l

# sql-exporter side
curl -s localhost:9399/metrics | grep -E '<expected metric prefixes>' | wc -l
```

Compare counts against the same recipe on the previous version. A drop is a
hazard.

For the postgres-exporter side specifically, also check the project's
release notes and issue tracker for `version-gated collector` flags — these
are typically opt-in and default-off, so the gating works only if you turn
it on.

### Fix shape

For postgres-exporter: enable the new opt-in collector flag in
`docker-compose.local.yml` under the `postgres-exporter` service's
`command:` block, and update Grafana panel PromQL to the new metric names.

For sql-exporter: split the affected collector query into (a) a reduced
query selecting only the columns that still exist on the old view and (b) a
new query against the relocated view, then update each metric definition's
`query_ref` to point at the right query.

### Worked example — PG17: `pg_stat_bgwriter` checkpoint columns relocate to `pg_stat_checkpointer`

PG17 split `pg_stat_bgwriter`, relocating the four checkpoint-specific
columns:

| Column (PG16 on `pg_stat_bgwriter`) | PG17 location                        |
|-------------------------------------|--------------------------------------|
| `checkpoints_timed`                 | `pg_stat_checkpointer.num_timed`     |
| `checkpoints_req`                   | `pg_stat_checkpointer.num_requested` |
| `checkpoint_write_time`             | `pg_stat_checkpointer.write_time`    |
| `checkpoint_sync_time`              | `pg_stat_checkpointer.sync_time`     |

postgres-exporter handles this with a version-gated collector that is
**opt-in and default-off**. The fix is the `--collector.stat_checkpointer`
flag plus PromQL updates:

```yaml
# docker-compose.local.yml
command:
  - "--collector.stat_statements"
  - "--collector.stat_statements.include_query"
  - "--collector.stat_checkpointer"   # Required from PG17 onward.
```

| Old PromQL (PG16)                                 | New PromQL (PG17+)                               |
|---------------------------------------------------|--------------------------------------------------|
| `pg_stat_bgwriter_checkpoints_timed_total`        | `pg_stat_checkpointer_num_timed_total`           |
| `pg_stat_bgwriter_checkpoints_req_total`          | `pg_stat_checkpointer_num_requested_total`       |
| `pg_stat_bgwriter_checkpoint_write_time_total`    | `pg_stat_checkpointer_write_time_total`          |
| `pg_stat_bgwriter_checkpoint_sync_time_total`     | `pg_stat_checkpointer_sync_time_total`           |

Panels in `docker/grafana/dashboards/db-server-health.json` reference these
by name and must be updated alongside the collector flag.

### Worked example — PG18: `pg_stat_wal` WAL-I/O columns relocate to `pg_stat_io`

PG18 removed the four WAL-I/O columns from `pg_stat_wal` and relocated them
to `pg_stat_io` with `object='wal'`:

| Column (PG14–PG17 on `pg_stat_wal`) | PG18 location                                  |
|-------------------------------------|------------------------------------------------|
| `wal_write`                         | `pg_stat_io.writes` where `object='wal'`       |
| `wal_sync`                          | `pg_stat_io.fsyncs` where `object='wal'`       |
| `wal_write_time`                    | `pg_stat_io.write_time` where `object='wal'`   |
| `wal_sync_time`                     | `pg_stat_io.fsync_time` where `object='wal'`   |

The remaining `pg_stat_wal` columns (`wal_records`, `wal_fpi`, `wal_bytes`,
`wal_buffers_full`) stay on the view.

Because this stack scrapes WAL stats via a sql-exporter custom collector
(not postgres-exporter's built-in), the all-or-nothing failure of hazard
class 2 applies: leaving the original combined query in place freezes all 8
`pg_wal_stat_*` metrics, not just the 4 that moved.

The fix in `docker/sql-exporter/wal-io.collector.yml`:

1. Reduce the original `pg_stat_wal` query to the columns that still exist:

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

2. Add a sibling query against `pg_stat_io`. See hazard class 3 below for
   why this query needs `SUM(...)` across the whole `object='wal'` partition
   rather than a `backend_type` filter:

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

3. Update the four affected metric definitions
   (`pg_wal_stat_write_total`, `pg_wal_stat_sync_total`,
   `pg_wal_stat_write_time_milliseconds_total`,
   `pg_wal_stat_sync_time_milliseconds_total`) to use
   `query_ref: pg_stat_io_wal`. Metric names are preserved, so the Grafana
   panels need no changes.

---

## Hazard class 2 — sql-exporter all-or-nothing per query

### Mechanism

sql-exporter's contract is: every metric declared under a `query_name` is
emitted from the result set of that single query. If the query fails to
parse or returns zero rows, **every metric under that query disappears**,
not just the ones whose columns are missing.

This amplifies hazard class 1 dramatically: a single missing column can
freeze a dozen metrics whose underlying state still exists.

### Detection

After any change to a sql-exporter collector file (including a server
version bump that affects columns referenced by an existing query):

```bash
# Pre-bump baseline
curl -s localhost:9399/metrics | grep '^<metric_prefix>' | wc -l > /tmp/before
# Post-bump
curl -s localhost:9399/metrics | grep '^<metric_prefix>' | wc -l > /tmp/after
diff /tmp/before /tmp/after
```

A drop in count is the symptom. Cross-reference against the collector file:
any metric whose `query_ref` matches a now-failing query is silently dead.

### Fix shape

Split the failing query along the boundary that the schema change
introduced. Keep one query for the columns that survived on the original
view, add a second query for the columns that moved. Re-point each metric's
`query_ref` to whichever query its source columns now live in.

Avoid the temptation to "merge them with a UNION" or "join in
sql-exporter" — both add complexity and neither is the right tool. Two
sibling queries with explicit `query_ref` per metric is the cheapest, most
debuggable form.

---

## Hazard class 3 — relocated metrics arrive with new label dimensions

### Mechanism

When the cumulative-statistics system relocates a metric from a
single-row, cluster-wide view to a multi-row, label-dimensioned view, the
naive replacement query can fail in two compounding ways:

1. **Cardinality explosion at the metric layer.** sql-exporter and
   Prometheus reject duplicate label sets ("was collected before with the
   same name and label values"). If the new view returns multiple rows per
   logical metric and the collector emits them as label-free, the scrape
   fails outright on the second row.
2. **Silent under-counting via incorrect filtering.** The instinctive fix
   for #1 is to filter to a single row with `WHERE backend_type = 'X'`
   based on the assumption that one backend does all the work. This is
   almost always wrong: cluster-wide totals on the old view summed
   contributions from many backends, and narrowing to one understates the
   metric — silently, without breaking any scrape.

### Detection

When relocating a metric across a view split, run the comparison query
*against the new view* on a live server before committing the collector
change:

```sql
SELECT backend_type, object, context, <metric column>
FROM <new view>
WHERE <relocation predicate>;
```

If this returns more than one row, you have label-dimensioned data and
must aggregate, not filter.

To confirm the aggregation is faithful to the old cluster-wide total,
compare the SUM against the old metric's last known value during a
side-by-side scrape window before the cutover.

### Fix shape

Aggregate with `SUM(...)` across the whole partition that the old
cluster-wide metric represented. Use `WHERE` only to scope the partition,
not to narrow within it.

### Worked example — PG18 `pg_stat_io` WAL writes

PG18 GA exposes WAL writes in `pg_stat_io` split by
`(backend_type, object, context)`. On a working stack, this returns 28
rows for `object='wal'` (14 backend types × 2 contexts: `init` and
`normal`).

Mapping evidence from a live PG18 instance under load:
`walwriter` contributed ~23 writes while the cluster-wide total was 819 at
the same instant — `standalone backend`, `client backend`, `checkpointer`,
`autovacuum worker`, `startup`, and `walsender` all contributed
non-trivially. A `WHERE backend_type='walwriter'` filter would have
under-counted by ~36×.

Additionally, the per-row `(init, normal)` context split means even with a
single-backend filter the query returns two rows, and Prometheus rejects
the second one with `"was collected before with the same name and label
values"`.

The correct form is `SUM(...)` across the whole `object='wal'` partition,
with no `backend_type` filter — this matches the old cluster-wide
semantics and produces a single label-free row.

```yaml
# Correct — semantically faithful, label-free
SELECT
  SUM(writes)     AS wal_write,
  SUM(fsyncs)     AS wal_sync,
  SUM(write_time) AS wal_write_time,
  SUM(fsync_time) AS wal_sync_time
FROM pg_stat_io
WHERE object = 'wal'
```

---

## Hazard class 4 — version-gated metrics that succeed *natively* on the new version

### Mechanism

A metric that required a workaround on version N (e.g. derived in PromQL via
a recording rule, or computed from a join because the underlying column
didn't exist) may become available natively on version N+1. The hazard is
not breakage but **drift**: the workaround keeps emitting alongside the
native series, both are correct, but their definitions can diverge subtly
(retention windows, label sets, sampling resolution).

### Detection

Walk the recording rules and custom collector queries. For each one whose
*reason for existing* was "the underlying state isn't queryable on
version N", check whether version N+1 makes it queryable directly.

### Fix shape

Drop the workaround in the same change that bumps the version. Repoint any
alert rule that referenced the derived series at the native series.
Document the removed workaround inline in the collector or rule file as a
header comment so future-you doesn't reintroduce it.

### Worked example — PG17 makes `pg_replication_slots.inactive_since` native

On PG16 there was no native column for "how long has this slot been
inactive." A PromQL recording rule
(`pg_replication_slot:inactive_age_seconds`) derived it from the
`pg_replication_slots_active` series with a 7-day `last_over_time` window.

PG17 introduced `pg_replication_slots.inactive_since` as a real column. The
PG18 cutover dropped the recording rule, added an `inactive_since` selection
to the sql-exporter `replication.collector.yml` collector, and re-pointed
the `ReplicationSlotInactive` alert at the native series.

The post-cutover behavior is also worth documenting: the column emits no
row when the slot is active (`inactive_since IS NULL`), so the *absence*
of the series is the "slot is healthy" signal — the alert correctly does
not fire on missing data.

---

## Upgrade procedure

For any major-version bump, in order:

1. **Bump the image tag** in `docker-compose.local.yml`.
2. **Walk the four hazard classes above** against the version's release
   notes and the project's collector/rule files. Record each affected
   collector query, recording rule, and dashboard panel before restarting
   the stack — dashboards will go silent between the DB coming up and the
   collector fixes landing if you skip this.
3. **Apply the fixes** in the same change as the version bump. Don't ship
   the version bump and chase the metric breakage afterward.
4. **Verify end-to-end** before declaring done. Each layer in turn:
   - **Producer:** the new SQL parses and returns rows
     (`docker compose exec postgres psql -c '<query>'`).
   - **Collector:** the metric appears in the exporter scrape
     (`curl -s localhost:9187/metrics | grep <name>` for postgres-exporter,
     `curl -s localhost:9399/metrics | grep <name>` for sql-exporter).
   - **Storage:** Prometheus has the series
     (`curl -s 'localhost:9090/api/v1/query?query=<name>'`).
   - **Presentation:** the Grafana panel renders fresh data.
   A green producer with a flat dashboard means a layer in between is
   broken; checking only the producer is not "verification."
5. **Re-baseline workload-derived alert thresholds** if the upgrade
   plausibly shifts the engine's behavior under load (PG17 → PG18 changed
   the I/O subsystem materially). Use the project's load profile
   (`mvn -Pgatling gatling:test`) — see ADR-008's calibration log for the
   workload shape and the "under-load baseline" sampling methodology.
   ADR-008 also documents which thresholds are workload-derived versus
   design-derived, and which are protected by structural-safety-net framing
   that survives a re-baseline drift.

If the upgrade uses `pg_upgrade --link` (the in-place path), the project
ships `docker/pg-upgrader/Dockerfile` as a starting point: a transient image
carrying both the previous-major and current-major binaries plus the
matching `pg_cron` extension packages, base-imaged on the same Debian
release as the runtime image to avoid glibc/collation drift across the
cutover. The Dockerfile names concrete major versions per cutover; update
those for the next bump.

## References

- PostgreSQL release notes per major version (the cumulative-statistics
  section is the relevant one for this document)
- postgres-exporter project — version-gated collector flags
- sql-exporter — collector configuration reference
- ADR-008 (`docs/adr/008-db-observability-slos.md`) — alert thresholds and
  re-baselining methodology
