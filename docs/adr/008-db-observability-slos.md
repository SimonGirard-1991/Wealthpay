# ADR-008: Database Observability SLOs and Alert Thresholds

## Status

Accepted

## Context

Earlier commits in this sequence wired up DB-level instrumentation that goes beyond postgres-exporter's
default collectors:

- [`b865acd`](../../docker/sql-exporter/replication.collector.yml) added a sql-exporter custom collector
  exposing `pg_replication_slots_wal_retention_bytes` — the disk-fill canary for logical-decoding
  consumers like Debezium that the built-in `pg_replication_slots_pg_wal_lsn_diff` (consumer-lag)
  metric misses.
- [`4abbbeb`](../../docker/prometheus/rules/db.rules.yml) shipped nine Prometheus rules across four
  groups: one recording rule (`pg_replication_slot:inactive_age_seconds`), one critical availability
  alert (`PostgresUnreachable`), two replication alerts (`ReplicationSlotWalRetentionHigh`,
  `ReplicationSlotInactive`), and five resource alerts (`DbConnectionsNearMax`, `DbCacheHitRatioLow`,
  `DbForcedCheckpointRatioHigh`, `PgStatStatementsLruEvicting`, `IdleInTransactionTooLong`).

The rule file documents each threshold inline, but the *methodology* behind threshold selection — and
the deliberate decisions that don't show up in code at all — needs a single source of truth that
survives file rewrites. Without one, the next engineer asked "this alert just paged me, should the
threshold change?" has no audit trail to read.

## Decision

We adopt three categories of threshold and document, per metric, which category applies, what the
measured baseline was at the time of writing, and what the threshold currently is.

### Threshold methodology

| Category | Definition | Re-tuning trigger |
|---|---|---|
| **Workload-derived** | Threshold computed from a measured steady-state baseline + safety margin. | Workload shape changes materially (different request mix, dataset size jumps an order of magnitude, new query patterns). |
| **Design-derived** | Threshold is a hard operational ceiling/floor independent of workload (e.g. "5 minutes idle in transaction is always a bug"). | Operational policy changes (e.g. SLO contract is renegotiated). |
| **Detector** | Threshold is qualitative — `rate > 0`, `absent()` — fires whenever a condition exists at all. | The condition stops being interesting (rare). |

Most rules are design-derived or detector-based on this stack. Only `DbCacheHitRatioLow` is
workload-derived, and it has a 100% measured baseline — see [Why DbCacheHitRatioLow exists despite
a 100% baseline](#why-dbcachehitratiolow-exists-despite-a-100-baseline) for why we still alert on it.

### SLI inventory

| Rule | Severity | Type | Threshold | At-rest baseline | Under-load baseline |
|---|---|---|---|---|---|
| `PostgresUnreachable` | critical | Detector | `up == 0 or pg_up == 0 or absent(pg_up)` for 1m | up=1, pg_up=1 | unchanged |
| `ReplicationSlotWalRetentionHigh` | critical | Design-derived | > 1 GiB for 5m | 325 KiB | 211–275 MiB ‡ |
| `ReplicationSlotInactive` | warning | Design-derived | inactive_age > 300s for 1m | 0s (slot active) | 0s |
| `DbConnectionsNearMax` | warning | Design-derived | numbackends/max_connections > 0.8 for 5m | 17% | 17% |
| `DbCacheHitRatioLow` | warning | **Workload-derived** | < 0.90 for 15m | 98.7% | 96.19% (PG18) ◊ |
| `DbForcedCheckpointRatioHigh` | warning | Design-derived | req/total > 0.3 for 10m | 0.43% | 0.000% |
| `PgStatStatementsLruEvicting` | warning | Detector | rate > 0 for 15m | 0/s | 0/s |
| `IdleInTransactionTooLong` | warning | Design-derived | max idle-in-tx > 300s for 5m | 0s | 0s |

Routing: the `severity` label drives the existing alertmanager split (critical → critical Discord
webhook, warning → default Discord webhook). No alertmanager change was needed for these rules.

† **WAL retention "under-load baseline" — historical PG16 figure.** The 23.48 MiB figure was sampled
from Prometheus during the 2026-04-29 Gatling run on PG16, while Debezium was still consuming. The
metric is a continuously-drained high-water mark, not a steady-state resting value: in a healthy
stack the slot drains back to the 325 KiB at-rest baseline within seconds of the producer pausing.

‡ **WAL retention "under-load baseline" — PG18 (2026-05-02 re-baseline).** Two snapshots taken 31 s
apart during the Gatling peak phase: 211 MiB and 275 MiB, with the slot active and Debezium
consuming. PG18's revised checkpointer + async I/O move more work between checkpoints, so the
in-flight WAL high-water mark is materially higher than PG16's 23.48 MiB while the slot is still
draining cleanly. Both figures are well within the 1 GiB rule threshold (~26% of stop). A post-spike
sample of 24 MiB at idle confirms the slot drains back to a small steady-state value within a
checkpoint cycle — the diagnostic heuristic below is unchanged.

For the 3am operator: under PG18 the implicit ceiling-when-healthy at this load profile is ~275 MiB
mid-load, draining to ~25 MiB within minutes of write traffic ending. Anything sustained materially
above ~275 MiB *with active write traffic*, or above ~50 MiB *without*, indicates the consumer is
lagging or stalled, not "normal after load". There is no defensible steady-state number short of the
1 GiB rule threshold; the diagnostic heuristic is "does retention drain within minutes of write
traffic ending?" — if no, investigate.

◊ **Cache-hit ratio under-load — PG18 (2026-05-02 re-baseline).** The PG16 baseline was 100.000%
(working set fit comfortably in `shared_buffers` and the workload's first-pass reads were negligible
within the cumulative window). On PG18 the **31 s peak-window delta** ratio is 0.9619 — measured as
Δblks_hit / (Δblks_hit + Δblks_read) between two snapshots taken inside the Gatling constant-load
phase. The ~4% drop is consistent with PG18's async I/O issuing more cold reads against the same
logical workload.

**Three measurement forms — do not confuse them when checking the re-baseline trigger:**
1. **Cumulative single-curl ratio** (`pg_stat_database_blks_hit / (blks_hit + blks_read)`) returns
   ~0.853 on this stack. This mixes the cluster's entire history (including cold cache after every
   restart) with the live workload. **Do NOT use** for the re-baseline check — it will spuriously
   trip the 0.95 trigger.
2. **31 s peak-window delta** (the 0.9619 figure above) is the *calibration* number — useful for
   "did the engine's behavior shift materially?" between major-version bumps, but tighter than
   what the alert actually evaluates.
3. **Production alert evaluation** uses Prometheus `rate(...)[10m]`, which dilutes the peak with
   the 9.5 minutes of surrounding load tail and idle time. On PG18 immediately post-Gatling this
   evaluates to ~0.913 (captured on the live PromQL expression during the 2026-05-02 re-baseline),
   already below the 0.95 re-baseline trigger.

**Re-baseline trigger evaluation (load-bearing):** evaluate against the *production alert form*
(`rate()` over `[10m]`), not against the calibration delta. The 0.913 reading immediately after a
load spike on the idle stack is the expected workload-tail behavior the ADR's structural-safety-net
framing anticipates, and is **resolved by re-sampling under sustained nominal load**, not by lowering
the threshold. If the live `rate()` expression evaluates ≥0.95 during sustained nominal load (i.e.
the application is serving real traffic, not a cold-cache idle stack), the structural-safety-net
framing remains valid; if it durably evaluates below 0.95 during nominal traffic, the working set
has genuinely outgrown shared_buffers and the alert moves from structural to workload-derived
(see "Re-baseline trigger (size-driven)" below).

### Calibration log

| Date | Workload | Tooling | Result |
|---|---|---|---|
| 2026-04-29 | Account lifecycle: ramp 1→100 RPS over 30s, hold 60s, ramp down. Reservation flow at 50 RPS same shape. Heavy-account: 1000 sequential deposits on a single aggregate. | `mvn -Pgatling gatling:test` against `localhost:8080`. PG16. | 41,681 requests / 100% success / p50=2ms / p95=4ms / p99=7ms / max=289ms. All Gatling assertions passed. |
| 2026-05-02 | PG18 post-upgrade re-baseline. Same workload shape as the 2026-04-29 run (no simulation changes). | `mvn -Pgatling gatling:test` against `localhost:8080`. PG18.3 via `pg_upgrade --link` from PG16. | 41,681 requests / 100% success / p50=3ms / p95=6ms / p99=9ms / max=249ms. All Gatling assertions passed. Under-load cache-hit ratio (delta) = 0.9619 (above 0.95 re-baseline trigger ✓). WAL retention high-water 211–275 MiB mid-load (~26% of 1 GiB stop). |

The under-load baseline column above is sampled from Prometheus immediately after this run. Per-metric
sampling queries are recorded inline in `db.rules.yml` rule comments. **Cache-hit ratio sampling
note:** delta-based (two `pg_stat_database_blks_*` snapshots ≥30 s apart inside the peak window),
not cumulative. The cumulative form mixes cluster lifetime cold reads with the live workload and
will silently understate the under-load ratio.

### Why `DbCacheHitRatioLow` exists despite a near-100% baseline

The Wealthpay working set is small enough to fit comfortably in `shared_buffers`. Both at-rest
(98.7%) and under-load buffer-cache hit ratios remain well above the 0.90 threshold (PG16 under-load
was 100.000%; PG18 under-load is 96.19% delta-based — see the ◊ footnote in the SLI inventory). No
plausible day-to-day workload shift will push the metric below 0.90.

The alert is kept because its *failure modes* are still real:

- A query plan switching to a sequential scan on a cold table (e.g. a regression after a schema or
  statistics change) will produce a sustained drop in hit ratio.
- A future migration that grows the working set past `shared_buffers` will produce a step-change drop.
- A misconfigured `shared_buffers` after a Postgres tuning change (e.g. the production tuning
  recommendation changes and is misapplied) will produce a sustained drop.

Treat this rule as a structural safety net, not an expected-to-fire signal. Re-evaluate the threshold
if and only if the under-load baseline durably drops below 0.95 — at that point the working set has
genuinely outgrown shared_buffers and the alert needs widening, not the threshold tightening.

**Re-baseline trigger (size-driven).** The "structural safety net" framing rests on an unstated
assumption: the working set of this banking workload will not exceed `shared_buffers` in any
plausible near-term scenario. For a system that grows monotonically (event_store, account_balance_view)
this is an *aggressive* assumption, not a safe one.

The buffer cache is pressured by *active working-set bytes*, not row count. The dominant
contributors on this stack are:

- `event_store` — grows monotonically forever; rehydration scans `version > snapshot.version` per
  aggregate read, so the hot tail of every active account is in the cache.
- `account_balance_view` — projection read model, typically hot end-to-end.
- `event_store` snapshots — bounded per account by `ACCOUNT_SNAPSHOT_THRESHOLD`, but contribute to
  cache pressure during rehydration.

Concrete primary trigger: re-baseline when

```sql
SELECT
  pg_total_relation_size('account.event_store')
  + pg_total_relation_size('account.account_balance_view')
```

approaches the configured `shared_buffers`. Rule of thumb: when the hot tables exceed ~70% of
`shared_buffers`, cache-hit ratio starts degrading visibly. That number is directly observable and
directly comparable to a Postgres setting, which is exactly what determines whether the alert is
structural-safety-net vs workload-derived.

Secondary proxy: 10× account growth from the calibration date is a reasonable second-best signal
when table-size measurement isn't available, but it is a *proxy* — a 10× increase in dormant
accounts moves working set very little, while a 10× increase in transactions-per-account moves it a
lot.

When the primary trigger fires: re-run the calibration workload, capture a fresh under-load baseline,
and decide whether `DbCacheHitRatioLow` has crossed from structural-safety-net to workload-derived.
At that point the threshold may need re-tuning, the `shared_buffers` setting may need raising, or
both.

### Why `inactive_age_seconds` was a recording rule on PG16 (historical context)

> **2026-05-02 update — applied.** This stack now runs PG18.3. The PG16-era recording rule
> `pg_replication_slot:inactive_age_seconds` has been removed; `ReplicationSlotInactive` now
> references `pg_replication_slots_inactive_since_seconds` directly (emitted by the sql-exporter
> collector, sourced from `pg_replication_slots.inactive_since` natively in PG17+). Section retained
> as historical context for the original mechanism and its rationale.

`pg_replication_slots.inactive_since` was added in PostgreSQL 17. While this stack pinned
`postgres:16` in `docker-compose.local.yml`, joining `pg_stat_replication` did not work as a
fallback because that view has *no row* for an inactive slot — there is nothing to subtract a
timestamp from.

Deriving the inactivity age in PromQL was the correct PG16 mechanism. The recording rule's subquery
used a 7-day window:

```promql
time() - last_over_time(timestamp(pg_replication_slots_active == 1)[7d:1m])
```

Cardinality cost was unchanged (one sample per slot per evaluation). The 7-day window guaranteed the
rule survived a Prometheus restart for a week of inactivity AND continued to fire if a slot was
inactive for the whole week.

**PG17 migration step (now applied as part of the 2026-05-02 PG18 upgrade):** drop the recording
rule, add a `SELECT inactive_since` column to `docker/sql-exporter/replication.collector.yml`, and
have `ReplicationSlotInactive` reference the new SQL-side metric directly. Tracked in the collector
header comment. Active behavior on PG18: the series is emitted only while the slot is inactive
(`inactive_since IS NULL` produces no row when active), so absence of the series is the correct
"slot is healthy" signal.

### Why `IdleInTransactionTooLong` cannot name the offending session

`pg_stat_activity_max_tx_duration` is computed inside postgres-exporter's collector via:

```sql
SELECT datname, state, MAX(EXTRACT(EPOCH FROM now() - xact_start))::float AS max_tx_duration
FROM pg_stat_activity GROUP BY datname, state;
```

The `GROUP BY` strips `application_name`, `usename`, and `pid` before they ever reach Prometheus.
Annotation interpolation of those labels is therefore a known footgun — the `{{ $labels.x }}`
template will render empty strings.

We embed the diagnostic SQL directly in the alert description instead:

```sql
SELECT pid, application_name, usename, xact_start, query
FROM pg_stat_activity
WHERE datname='wealthpay' AND state='idle in transaction'
ORDER BY xact_start LIMIT 5;
```

If idle-in-tx becomes a recurring incident, escalate to a sql-exporter collector that emits a
per-pid `pg_stat_activity_idle_in_tx_seconds{pid, application_name, usename}` series and rewrite this
alert to fire on it. Cardinality is bounded (Hikari pool size + a handful of human sessions).
Currently out of scope; the 3am SQL is sufficient for a once-a-quarter alert.

### Fire-drill procedure

Each critical alert must have been provoked at least once on this stack with a recorded screenshot
of the Discord delivery before the alert is considered production-ready. Procedure:

| Alert | Drill | Expected delivery |
|---|---|---|
| `PostgresUnreachable` | `docker compose stop postgres` and wait 2m. (1m is the `for:` window — at-the-boundary waits are race-prone with 15s scrape + 15s eval intervals.) | Critical alert in `#critical` Discord channel. Recover with `docker compose start postgres`. |
| `ReplicationSlotWalRetentionHigh` | On a throwaway branch, temporarily lower the rule expression to `> 10485760` (10 MiB). `docker compose stop kafka-connect`, then `mvn -Pgatling gatling:test` to drive ~25 MiB of WAL through the slot. Wait for the `for: 5m` window. Restore the threshold and `docker compose start kafka-connect`; the slot drains within minutes. (Provoking the real 1 GiB threshold on the local stack would require synthetic WAL generation — `pg_logical_emit_message` in a tight loop or pgbench at scale — and is left for the production fire-drill.) |
| `ReplicationSlotInactive` | `docker compose stop kafka-connect` and wait 7m. (Math: recording rule starts ticking from 0 immediately when the slot goes inactive, crosses 300s at t=5m, `for: 1m` window completes at t=6m. 6m is at-the-boundary — same race condition that pushed `PostgresUnreachable` to 2m. 7m gives slack.) | Warning alert. Recover with `docker compose start kafka-connect`. |
| `IdleInTransactionTooLong` | Open an interactive psql session: `docker compose exec postgres psql -U user -d wealthpay`. Inside the session run `BEGIN; SELECT 1;` and leave the terminal alone for 11 minutes. (Critical: the session must be in `state='idle in transaction'` — i.e. transaction open, no query running. Do NOT use `BEGIN; SELECT pg_sleep(900);` — `pg_sleep` keeps `state='active'` for the entire sleep, the rule's `state="idle in transaction"` filter never matches, and the drill silently does nothing. Math after the session is in the right state: `max_tx_duration` ticks from `xact_start`, crosses 300s at t=5m, `for: 5m` window completes at ~t=10m → wait 11m for slack.) | Warning alert. Recover with `COMMIT;` or `\q` in the psql session. |

Fire-drill execution evidence (Discord screenshots, recovery confirmation) lives in the closing
PR's description, not here — drills are performed once and recorded; this ADR captures the
*procedure*.

## Consequences

### Positive

- **Audit trail** — threshold rationale is captured at architectural depth, not just point-of-use
  comments that get lost when rules are reorganized.
- **Re-tuning is principled** — workload-derived thresholds have a documented baseline; design-derived
  thresholds have a documented policy basis. The next engineer asked to change a number knows which
  conversation to have.
- **Non-goals are legible** — explicitly deferred items (next section) prevent future ADRs from
  accidentally re-litigating scope.

### Negative

- **Drift risk** — the calibration log goes stale as workload changes. Convention: any rule whose
  threshold changes triggers an ADR amendment in the same PR.
- **Two sources of truth for some content** — the inline rule comments in `db.rules.yml` and the
  ADR will say overlapping things. Mitigated by keeping the ADR architectural and the rule comments
  point-of-use, but tension is real.

## Alternatives Considered

### Inline comments only in `db.rules.yml`

**Rejected.** Comments survive line-level edits but not file-level reorganization. A wholesale
rewrite of the rule file (e.g. when migrating to a new alerting framework) takes the rationale
with it. The ADR persists across rule-file refactors.

### Grafana dashboard annotations

**Rejected.** Annotations require interactive Grafana access. The on-call hitting a Discord alert at
3am may not have it (VPN issues, Grafana itself may be the broken thing). Alert descriptions and an
ADR pointer reachable from the repo are more robust.

### Wiki page

**Rejected.** A wiki has real strengths — rich formatting, full-text search, editable by ops staff
who don't have repo access. The decisive disadvantage is review visibility: wiki content is *not*
shown in the PR diff when a threshold changes in `db.rules.yml`, so drift between the wiki and the
rule file becomes the default case rather than the exception. The ADR sits next to the rule file in
the same git history, which forces a PR that changes a threshold to either amend the ADR in the same
diff or explicitly explain why not — flipping the default from drift to consistency.

## Explicitly deferred

The following items are out of scope for this ADR. Each has a separate trigger for re-engagement:

- **Per-query slow-query regression alert.** Verification during planning surfaced that
  postgres-exporter (≥ v0.18.0; this stack runs v0.19.1) already emits
  `pg_stat_statements_query_id{queryid, query} 1` (a constant-value info-pattern metric, top 100 by
  total exec time, untruncated), in addition to per-queryid numerics like
  `pg_stat_statements_seconds_total{datname, queryid, user}`. An earlier draft of this ADR called
  for a custom sql-exporter collector to provide the info series — that collector would have
  duplicated the built-in. The regression alert is therefore feasible **today** using only built-in
  metrics. **Prerequisite:** both `--collector.stat_statements` and
  `--collector.stat_statements.include_query` must be enabled on the exporter; both are default-off
  upstream. The local stack enables them in `docker-compose.local.yml`; production should review
  the cardinality footprint of `include_query` (untruncated query text on every series) before
  turning it on. Sketch:

  ```promql
  (
    rate(pg_stat_statements_seconds_total{datname="wealthpay"}[15m])
    >
    3 * rate(pg_stat_statements_seconds_total{datname="wealthpay"}[1h] offset 24h)
  )
  * on(queryid) group_left(query) pg_stat_statements_query_id
  ```

  Fires when a queryid's 15m exec-time rate is more than 3× its rate 24h ago, with the query text
  joined in for the alert annotation. Three caveats for the operator wiring this up:

  - **Detects regressions of already-tracked queries only.** The comparison requires the queryid
    to exist on BOTH arms of the rate expression (current and 24h-offset). A query that is hot
    today but wasn't in the top-100 24h ago — the classic "missing index turned a previously-cheap
    query pathological" / "new hot query" scenario — has no right-hand-side series, so the
    expression silently produces no result rather than firing. This alert is for *"watch the
    queries we're already watching, page if any of them got 3× worse"*; the orthogonal "we lost
    track of a hot query" case is left to the existing `PgStatStatementsLruEvicting` placeholder.
  - **queryid stability is not absolute.** queryids can shift on major version upgrades (always),
    on `DROP`/`CREATE` of referenced objects (a deployment that replaces a function or recreates
    a table will mint new queryids for queries that touched it), on cross-architecture replicas,
    and — rarely, as a documented "last resort" — on minor upgrades. After any of these, the
    `offset 24h` arm has no matching series for affected queries, producing false negatives for
    ~24h. Document in the upgrade and migration runbooks.
  - **Series rotate as queries enter and leave the exporter's top-100.** When a queryid drops out
    of the top-100, both `pg_stat_statements_seconds_total` and `pg_stat_statements_query_id` stop
    being emitted for it (Prometheus inserts an implicit staleness marker on the next scrape).
    Both arms of the alert fall off in lockstep, so the failure mode is "alert stops being
    possible for that queryid" rather than "alert fires with an empty annotation." Acceptable for
    a warning-tier alert; if you need historical query-text recovery, query `pg_stat_statements`
    directly from the DB by queryid while the row is still in the in-memory hashtable.

  Reason this remains deferred (not shipped now): the `3×` regression factor and the `24h` offset
  are workload-tuning choices that need a longer calibration baseline than this ADR's single
  Gatling run provides — weekday/weekend mix, batch-job patterns, end-of-month effects. Re-engages
  once the service has accumulated ≥2 weeks of production-shape traffic to baseline against, or
  when a specific regression incident motivates ad-hoc tuning. The current
  `PgStatStatementsLruEvicting` alert remains the placeholder for "your Top-N panel may be
  incomplete" in the meantime.
- **OpenTelemetry JDBC tracing spans.** Correlates with distributed traces but does not affect
  alerting. Separate workstream.
- **Production-mode secrets for the monitoring role.** The local stack hard-codes `monitoring/monitoring`
  in `docker/sql-exporter/sql_exporter.yml`. Production needs a Vault/ASM-sourced DSN; the file
  header already flags this. Re-engages when this stack is lifted to a non-local environment.
- **Periodic `pg_stat_activity` forensic snapshots.** Useful for post-incident triage. Lower
  priority than the alert work above.
- **Debezium Kafka Connect JMX metrics.** Connect-side observability is its own concern; the
  retention/consumer-lag alerts here cover the Postgres side of the same failure mode.
- **Alert hysteresis (`keep_firing_for`).** None of the rules in this batch use Prometheus 2.42+'s
  `keep_firing_for` clause. On a metric that flaps near its threshold (e.g. `DbConnectionsNearMax`
  hovering around 80%), this can produce notification spam — alert resolves, fires again, resolves,
  fires again. Acceptable for the current intensity of these alerts (warnings, mostly), but worth
  re-engaging if any rule produces &gt;3 firings per day in steady state. Re-engagement criterion:
  notification fatigue, measured as on-call complaints or alert acknowledgement-but-no-action rates.
