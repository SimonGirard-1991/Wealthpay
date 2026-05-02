# PostgreSQL 16 → 18 Migration Plan

> **Status:** Executing — Phases 0, 2, 2.9, 3, 4a, 4.5, 5, 6 are all EXECUTED (2026-05-01 / 2026-05-02). Phase 1 is doc-only and DECIDED. **Path C** chosen for Phase 6 (new connector identity `wealthpay-outbox-connector-pg18` + `slot.name=debezium_pg18`, `snapshot.mode=never`); end-to-end smoke test PASSED. Remaining: [Phase 7](#phase-7--final-verification-cdc-consistency--metrics-behaviour) (R2 + R3 verification under Gatling spike) and [Phase 8](#phase-8--re-baseline-adr-008--merge) (re-baseline ADR-008 + merge). Distributable to agents per phase, but the BLOCKING gates must be respected — agents executing Phase 3+ must verify Phase 2.9 has been signed off by the human owner.
> **Execution progress.** Phase 0 executed 2026-05-01; baseline captured to `~/wealthpay-pg-upgrade-baseline/` (8 files, all acceptance criteria met — see [Phase 0 status block](#phase-0--pre-flight-inventory--baseline-read-only)). Phase 1 is doc-only and already DECIDED. Phase 2 executed 2026-05-01 — both the Java track (2.0, commit `734cc46`) and the infra track (2.1–2.8, this PR's infra commit) on `feat/postgres-migration`; verification suite green (228 tests pass against PG18 Testcontainers, `docker buildx build docker/postgres/` succeeds, `promtool check rules` reports 0 errors on the 8 remaining rules) — see [Phase 2 status block](#phase-2--code--config-changes-single-pr-lands-but-does-not-run). Phase 2.9 executed 2026-05-01 — `wealthpay-pg-upgrader:18` image built from `docker/pg-upgrader/Dockerfile`; `pg_upgrade --check` and `pg_upgrade --link` both exited 0 against the rehearsal clone; rehearsed PG18 cluster booted with the runtime image, reported PG18.3, preserved every contract-table row count exactly, and the pre-existing collation warning resolved as predicted. Six findings fed back into Phase 3 step 6, Phase 4a steps 1–2, and Phase 5 (see [Phase 2.9 status block](#phase-29--build-and-rehearse-the-pg-upgrader-image-blocking-gate-before-phase-3)). Phase 3 executed 2026-05-01 — `~/wealthpay-pg-upgrade-baseline/dumpall.before.sql` captured (824 MB, all 7 contract tables present); `debezium` slot dropped (post-drop count = 0); stack stopped cleanly (`wealthpay-postgres` `Exited (0)`, no `postmaster.pid`, `pg_replslot/` empty); working tree returned to `HEAD` (`cc49cba`) after off-plan-boot recovery (see [Phase 3 status block](#phase-3--drain--backup-cluster-state-touching-immediately-precedes-upgrade)). Phase 4a executed 2026-05-01 — `pg_upgrade --link` exit 0, `Upgrade Complete`; PG18.3 came up healthy under the rebuilt `wealthpay-postgres:latest`; pg_hba.conf carried forward from PG16 pre-cliff (newly surfaced finding, folded into step 5); `vacuumdb` and `ALTER EXTENSION pg_stat_statements UPDATE` succeeded on the human side. Two real-execution findings — `--user postgres` + `--workdir <writable>` for `pg_upgrade`, and the pg_hba.conf copy — folded into Phase 4a steps 2 and 5 respectively (see [Phase 4a status block](#phase-4a--execute-pg_upgrade---link-chosen-method)). Phase 4.5 executed 2026-05-02 — PG18.3 confirmed; `flyway_schema_history` strict diff empty (18 rows, V1–V17, all `success=t`); preserved-table strict diff **empty** (`event_store=2 124 327`, `account_balance_view=718 004`, `account_snapshot=35`, `processed_transactions=1 159 354`, `processed_reservations=248 088`, `outbox_cleanup_log=19`); outbox informational diff also empty (`41 681`); pg_cron history shows latest run `2026-05-01 03:00` (pre-upgrade) so no cron writes since cluster start — **R1 contract proven**. Two real-execution findings — Phase 4.5 step 4 SQL referenced columns that do not exist on `cron.job_run_details` (`schedule`, `last_run_status`); Phase 3 step 5's deferred connector DELETE caused the legacy `wealthpay-outbox-connector` to auto-rehydrate from `connect-configs` on the new stack and recreate a fresh `debezium` logical slot (R1 unaffected because Debezium is a CDC reader and `table.include.list=account.outbox`, whose count is unchanged). Folded into Phase 4.5 step 4 (corrected SQL) and Phase 6 step 0 (explicit cleanup of the legacy connector + slot before offset-path inspection) respectively (see [Phase 4.5 status block](#phase-45--post-upgrade-data-preservation-check-before-any-new-write)). Phase 5 executed 2026-05-02 — all eight steps PASS after one fix landed (`docker/sql-exporter/wal-io.collector.yml`: `pg_stat_io_wal` query rewritten from `WHERE backend_type='walwriter'` to `SUM(...)` across all `object='wal'` rows). Step 1's 3-check protocol caught the bug exactly as designed (HTTP `up=1` throughout, but logs and Prometheus per-target `lastError` would have shown the duplicate-collection error if the latter had been visible — the log scan was the actual detector). The duplicate stems from `pg_stat_io` having two rows per `(backend_type, object)` due to the `(init, normal)` context split on PG18 GA, so the labels-free metrics emitted twice. The wider-than-expected fix (drop `backend_type='walwriter'` entirely, SUM across all rows) also corrects a semantic understatement: walwriter is only one of seven backend types issuing WAL writes on this stack — filtering to it understated cluster-wide writes by ~36×. WAL records advanced 44 127 → 146 045 under the SQL-only probe; checkpointer `num_requested_total` advanced 7 → 8 (the explicit `CHECKPOINT;`); all 8 `pg_wal_stat_*` series and 9 `pg_stat_checkpointer_*` series present and clean; metric inventory diff bounded to (a) the canonical PG17 `pg_stat_bgwriter` → `pg_stat_checkpointer` migration and (b) PG18 GUC drift on `pg_settings_*` — no contract-bearing metric missing. Three runbook gaps folded back: (i) Phase 2.4 fix recipe in [postgres-upgrade.md §PG18](postgres-upgrade.md#pg18--pg_stat_wal-columns-relocate-to-pg_stat_io) lines 130–141 (replace `WHERE backend_type='walwriter'` with `SUM(...)` across `object='wal'`); (ii) Phase 5 step 4 expected line count is `≥ 4` (the four core renames present), not exactly 4 — postgres-exporter v0.19.1 emits the full 9-series view including standby-only restartpoints + `stats_reset_total`; (iii) Phase 5 step 6 `up{job=~".*postgres.*"}` regex misses `sql-exporter` — broaden to `up{job=~".*(postgres|sql).*"}` or just `up`. Full execution log at `~/wealthpay-pg-upgrade-baseline/phase-5-execution.md` (see [Phase 5 status block](#phase-5--bring-up-new-stack--verify-metric-pipeline)). Phase 6 executed 2026-05-02 — Path C (new connector `wealthpay-outbox-connector-pg18` + new slot `debezium_pg18` + `snapshot.mode=never`); the predicted Phase 4.5 carryover was real (legacy connector + `debezium` slot both live, `active_pid=96`) and Step 0 prep cleaned it (DELETE → 5 s wait → drop slot, all green); offset inspection found 12 stored offsets for the legacy name with top LSN ≈ `2/CF464ED0`, well behind PG18's current `2/E7C4F8B8` — Path A would have been safe but Path C avoids the question; connector + task RUNNING; slot `debezium_pg18` `active=t, retention 7.4 KB`; smoke test (`POST /accounts`) returned `accountId=9e55ce31-…`; `account_balance_view` rowcount 718 004 → 718 005 within 3 s; balance `100.0000` projected — full WAL → Debezium → Kafka → consumer → projection chain healthy. Three runbook drifts folded back into Phase 6: (i) Step 0 inspection used wrong kafka service name + listener + topic name (`kafka` vs `kafka-1`–`kafka-3`, `localhost:9092` vs the INTERNAL listener `kafka-1:29092`, `connect-offsets` vs the configured `connect_offsets`); (ii) Step 6 `mvn spring-boot:run` needs a `set -a && source .env && set +a` preamble in a fresh shell or it hits `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${DB_URL}`; (iii) Step 7 smoke-test snippet drifted from the OpenAPI contract — real path is `POST /accounts` with flat `{accountCurrency, initialAmount, initialAmountCurrency}` and no `ownerId`. **Next agent should start [Phase 7](#phase-7--final-verification-cdc-consistency--metrics-behaviour)** — stack remains up, application is running, Debezium is streaming.
> **Plan-wide conventions (added after Phase 0 execution, applies to every phase that runs psql against the live cluster):**
>   - **Compose invocation.** This repo's compose file is `docker-compose.local.yml` (and `docker-compose.local.linux.yml` on Linux), not the default `docker-compose.yml`. Plain `docker compose exec …` fails with `no configuration file provided`. Use `./scripts/infra.sh exec …` (it applies the right `-f` flags) — or pass `-f docker-compose.local.yml` explicitly. The Phase 0 commands have been rewritten to use `./scripts/infra.sh exec`; later phases still show plain `docker compose exec` and the executing agent must apply this same substitution.
>   - **Flyway schema-history table location.** It lives in the **`account` schema** (Flyway is configured per-BC), so SQL must say `FROM account.flyway_schema_history` — not `FROM flyway_schema_history`. The Phase 0 step has been corrected; identical SQL appears in Phase 4.5 step 2 and Appendix A and must be qualified the same way.
>   - **stderr noise from psql.** The DB currently emits `WARNING: database "wealthpay" has a collation version mismatch` (DB built with collation 2.41, OS provides 2.36). It is pre-existing and orthogonal to this migration. Capture commands MUST redirect only `stdout` to baseline files; merging `stderr` (`2>&1`) contaminates the strict-diff target. Phase 4.5/5 agents should re-check whether the post-upgrade PG18 image still emits this; either way, the `before` files captured in Phase 0 are clean.
> **Owner:** _unassigned human_. Replace before circulation.
> **Decisions locked in.** Method: `pg_upgrade --link`. Debezium: `snapshot.mode=never`. See [Appendix B — Decision log](#appendix-b--decision-log). Agents do not re-litigate these.
> **Agent assignments.** Each phase header carries its `Assigned agent` line. The Java-side audit (Phase 2.0) goes to `java-backend-architect`; everything else is `general-purpose` with `code-reviewer` consulted at the Phase 7 PR gate. Irreversible steps ([Phase 4a step 5](#phase-4a--execute-pg_upgrade---link-chosen-method) and [Phase 8 merge](#phase-8--re-baseline-adr-008--merge)) end agent autonomy and require human action.
> **Companion doc:** [docs/postgres-upgrade.md](postgres-upgrade.md) — observability-hazard reference. Read it before starting Phase 5; the metric-relocation fix recipes are not duplicated here.

---

## TL;DR

- **Goal.** Move the Wealthpay local stack from `postgres:16-bookworm` to `postgres:18-trixie` in one hop, skipping PG17 entirely. The base-OS flip from bookworm (Debian 12) to trixie (Debian 13) is deliberate — it restores the project's original pre-`bd3d365` "moving major-tag" convention and aligns the runtime glibc with the catalog's stamped collation version (resolves the pre-existing `collation version mismatch` WARNING at the same time as the major bump).
- **Method (decided): `pg_upgrade --link`** via a transient upgrade container — see [Phase 4a](#phase-4a--execute-pg_upgrade---link-chosen-method). Chosen explicitly over `pg_dumpall + restore` because this is a learning project where production-realism matters more than rollback simplicity, and the planner-statistics preservation is a real benefit. Phase 4b is retained as a documented fallback only — it is **not** the chosen path and agents should not execute it without re-opening Phase 1.
- **Debezium snapshot mode (decided): `snapshot.mode=never`.** Production-strict behaviour, no duplicate Kafka traffic. The trade-off is brittleness on the freshly-recreated slot (Phase 6.3 details the contract); this is accepted for production-realism reasons.
- **Two hazards documented in the companion doc are real and must both be applied,** because we leapfrog PG17:
  1. PG17 hazard — `pg_stat_bgwriter` checkpoint columns relocate to `pg_stat_checkpointer`.
  2. PG18 hazard — `pg_stat_wal` WAL-I/O columns relocate to `pg_stat_io WHERE object='wal'`.
- **Four additional concerns this plan introduces** that the companion doc does not cover:
  - PG18 Docker image changes `PGDATA` from `/var/lib/postgresql/data` to `/var/lib/postgresql/18/docker` and changes the declared `VOLUME` to `/var/lib/postgresql`. The existing named-volume mount in [`docker-compose.local.yml`](../docker-compose.local.yml) breaks on a naive image-tag bump.
  - `pg_upgrade` preserves logical replication slots **only when the source cluster is ≥ PG17**. We are on PG16, so the Debezium slot will not survive — it must be drained, dropped, and recreated, with the connector restarted in `snapshot.mode=never`.
  - PG18 ships with `io_method=worker` enabled by default (asynchronous I/O). This is a behaviour change relative to PG16's synchronous I/O. The under-load baselines in [ADR-008](adr/008-db-observability-slos.md) need re-sampling after the upgrade — they are not invalid, but they are no longer the *current* baseline.
  - The base-OS flip from bookworm to trixie bumps glibc from 2.36 to ~2.41 (and OpenSSL 3.0 → 3.5, libicu72 → libicu76). For B-tree text indexes the locale-library version is part of the sort-order contract — `pg_upgrade --check` validates it. The flip is *with* us here: it brings runtime glibc in line with the catalog's stamped 2.41, eliminating the pre-existing collation-version warning rather than leaving it as a latent index-correctness footgun. Same ADR-008 re-sample covers it; no extra acceptance burden, but Phase 5 should explicitly note all three drift sources (PG cumulative-stats reset, `io_method=worker`, glibc 2.36 → 2.41) when the new baseline is published.

---

## Hard requirements

These are the constraints derived from the original brief. Each phase below names which requirement(s) it owns.

| # | Requirement | Owned by phase(s) |
|---|---|---|
| R1 | **All data preserved.** `event_store`, `account_balance_view`, `account_snapshot`, `processed_transactions`, `processed_reservations`, `outbox_cleanup_log` rows survive the upgrade. The `outbox` partitions are exempt (3-day retention; CDC has already drained them). | 3, 4a/4b, **4.5** (the strict diff lives here) |
| R2 | **Metrics behaviour preserved.** Every series emitted by `postgres-exporter` and `sql-exporter` before the upgrade is still emitted with the same name and labels after, including the eight `pg_wal_stat_*` and the four checkpoint counters. Every alert in [`db.rules.yml`](../docker/prometheus/rules/db.rules.yml) still fires on the same conditions. Every panel in `db-server-health.json` and `db-query-performance.json` renders. | 2, 5, 7 |
| R3 | **No silent CDC data loss.** Debezium's outbox stream resumes without missing events between application stop and start. Duplicates are acceptable (downstream idempotency handles them via `processed_transactions` / `processed_reservations`); silent drops are not. | 3, 6 |
| R4 | **Recoverable.** A failed upgrade can be rolled back to a known-good PG16 state with bounded data loss (zero, if rollback happens before the new cluster has accepted any write). | 0, 3, Rollback |

### Non-goals

Explicitly **out of scope** for this migration; do not let agents drag them in:

- Switching `io_method` away from the PG18 default (`worker`). The default is fine for local; production tuning is a separate workstream.
- Promoting MD5 → SCRAM password migration as a precondition. PG14+ already defaults to SCRAM, the existing `monitoring` role uses the default `password_encryption`, and the PG18 deprecation is a *warning*, not a removal. Re-engage when MD5 is actually removed (likely PG19 or later).
- Adopting PG18 features (UUIDv7, virtual generated columns, skip-scan plans). The migration is mechanical; feature adoption follows.
- Re-tuning `shared_buffers` / `work_mem` / `max_connections`. The current values are PG16 defaults and will be PG18 defaults too.
- Bumping postgres-exporter past v0.19.1 unless Phase 5 finds it strictly necessary. Version-bump scope creep is a known way to silently break unrelated metrics.

---

## Phase 0 — Pre-flight inventory & baseline (READ-ONLY)

**Status (2026-05-01): EXECUTED.** Baseline captured at `~/wealthpay-pg-upgrade-baseline/` — 8 files, all acceptance criteria met. Slot lag was 916 KB (well under 50 MB), all 8 `pg_wal_stat_*` and 4 `pg_stat_bgwriter_checkpoint*` series present. Preserved row counts at baseline: `event_store=2 124 327`, `account_balance_view=718 004`, `account_snapshot=35`, `processed_transactions=1 159 354`, `processed_reservations=248 088`, `outbox_cleanup_log=19`. Outbox (informational): `41 681`. Flyway head: V17 (outbox enable pg cron), 18 history rows, all `success=t`. Active alerts: only the always-firing `Watchdog` (heartbeat alert; expected). **Pre-existing observation that is *expected to resolve itself* during the upgrade:** psql emits `WARNING: database "wealthpay" has a collation version mismatch` (DB built with collation 2.41, OS provides 2.36). The `2.41` matches glibc 2.41 (trixie); the `2.36` matches glibc 2.36 (bookworm). The Phase 2.1 image flip from `postgres:16-bookworm` to `postgres:18-trixie` aligns runtime glibc with the catalog's stamped value, so the warning **should disappear** on first connection to the post-upgrade cluster. Phase 5 step 1 (post-upgrade `psql -c "SELECT version();"`) is the natural place to confirm this. If the warning persists after Phase 5 step 1 against the trixie-based PG18 image, that's an unexpected outcome and should be escalated rather than ignored. **Next phase:** Phase 2 (the Java track 2.0 in parallel with the infra track 2.1–2.8). Phase 1 is decision-only and frozen.

**Assigned agent.** `general-purpose`. Pure read-only Bash/curl/psql; nothing Java-side.

**Goal.** Capture a complete pre-upgrade snapshot so post-upgrade verification has something to compare against. Nothing in this phase mutates the cluster, the volumes, or any compose service.

**Scope.** ~30 minutes including data capture.

**Inputs.** Running PG16 stack, all dashboards green, Debezium connector in RUNNING state.

**Repo invocation note.** This repository's compose file is `docker-compose.local.yml` (and `docker-compose.local.linux.yml` on Linux), not the default `docker-compose.yml`. Plain `docker compose exec …` therefore fails with `no configuration file provided`. Use `./scripts/infra.sh exec …` (it forwards args after applying the right `-f` flags) — or pass `-f docker-compose.local.yml` explicitly. The commands below use `./scripts/infra.sh exec`. This convention applies to every later phase that runs `psql` against the running cluster (Phase 3, Phase 4.5, Phase 5).

**Steps.**

1. Verify the stack is in a clean state:
   ```bash
   ./scripts/infra.sh ps
   curl -sf http://localhost:8083/connectors/wealthpay-outbox-connector/status | jq '.connector.state, .tasks[].state'
   # Expected: "RUNNING" "RUNNING"
   ```
2. Capture the metric-name inventory **from each exporter** before the upgrade. Write to a file alongside this plan:
   ```bash
   mkdir -p ~/wealthpay-pg-upgrade-baseline
   # Strip labels with sed (otherwise `awk '{print $1}'` keeps `metric{label="value"}` and the diff is noisy/false-positive).
   curl -s localhost:9187/metrics | sed -nE 's/^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?[[:space:]].*/\1/p' | grep -E '^pg_' | sort -u > ~/wealthpay-pg-upgrade-baseline/postgres-exporter.before
   curl -s localhost:9399/metrics | sed -nE 's/^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?[[:space:]].*/\1/p' | grep -E '^pg_' | sort -u > ~/wealthpay-pg-upgrade-baseline/sql-exporter.before
   ```
3. Capture the eight `pg_wal_stat_*` and four `pg_stat_bgwriter_checkpoint*` series with their current values:
   ```bash
   curl -s localhost:9399/metrics | grep -E '^pg_wal_stat_' > ~/wealthpay-pg-upgrade-baseline/wal-stat.before
   curl -s localhost:9187/metrics | grep -E '^pg_stat_bgwriter_checkpoint' > ~/wealthpay-pg-upgrade-baseline/checkpoint.before
   ```
4. Confirm Debezium has fully drained the slot:
   ```bash
   ./scripts/infra.sh exec -T postgres psql -U user -d wealthpay -c \
     "SELECT slot_name, active, pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn) AS lag_bytes,
             pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS retention_bytes
        FROM pg_replication_slots;"
   ```
   Confirm `active=t` and `lag_bytes` is single-digit MB or less. If lag > 50 MB, hold and let the consumer drain before progressing.
5. Capture row counts for the data-preservation contract (R1) into **two separate files** — preserved-only and outbox-informational. Splitting them is what makes Phase 4.5 agent-safe: the strict diff is over the preserved file (must be empty), and the outbox count is captured for context (allowed to differ due to 3-day partition retention; not part of the preservation contract). The redirects deliberately keep stderr on the terminal — `psql` emits a `collation version mismatch` WARNING on this DB, and merging it into the file would contaminate the strict-diff target:
   ```bash
   # Preserved tables — strict diff target. The Phase 4.5 acceptance criterion is "this file's diff is empty."
   ./scripts/infra.sh exec -T postgres psql -U user -d wealthpay -F$'\t' -c \
     "SELECT 'event_store' AS t, count(*) FROM account.event_store
      UNION ALL SELECT 'account_balance_view',     count(*) FROM account.account_balance_view
      UNION ALL SELECT 'account_snapshot',         count(*) FROM account.account_snapshot
      UNION ALL SELECT 'processed_transactions',   count(*) FROM account.processed_transactions
      UNION ALL SELECT 'processed_reservations',   count(*) FROM account.processed_reservations
      UNION ALL SELECT 'outbox_cleanup_log',       count(*) FROM account.outbox_cleanup_log;" \
     > ~/wealthpay-pg-upgrade-baseline/row-counts.preserved.before
   # Outbox — informational only. Captured for the PR description; never part of an acceptance diff.
   ./scripts/infra.sh exec -T postgres psql -U user -d wealthpay -F$'\t' -c \
     "SELECT 'outbox (all partitions)' AS t, count(*) FROM account.outbox;" \
     > ~/wealthpay-pg-upgrade-baseline/row-counts.outbox.before
   ```
6. Capture the Flyway schema-history fingerprint. The history table lives in the **`account` schema** (not `public`) — Flyway is configured per-BC, so the fully-qualified `account.flyway_schema_history` is required:
   ```bash
   ./scripts/infra.sh exec -T postgres psql -U user -d wealthpay -c \
     "SELECT installed_rank, version, description, success FROM account.flyway_schema_history ORDER BY installed_rank;" \
     > ~/wealthpay-pg-upgrade-baseline/flyway.before
   ```
7. Capture the active alert state from Prometheus (so a post-upgrade silent-firing change is detectable):
   ```bash
   curl -sG http://localhost:9090/api/v1/alerts | jq '.data.alerts[] | {state, labels}' \
     > ~/wealthpay-pg-upgrade-baseline/alerts.before
   ```

**Verification & acceptance criteria.**
- All baseline files captured by steps 2–7 exist and are non-empty (the row-counts split in step 5 produces two files — `row-counts.preserved.before` and `row-counts.outbox.before` — so the directory contains more than seven files; the contract is "every step that captures, captured", not a fixed count).
- `pg_wal_stat_write_total`, `pg_wal_stat_sync_total`, `pg_wal_stat_write_time_milliseconds_total`, `pg_wal_stat_sync_time_milliseconds_total`, plus the four `_records / _fpi / _bytes / _buffers_full` counters are all **present** in `wal-stat.before`. Eight entries. Values may be zero on a quiet stack — presence is the contract here, "advances under load" is enforced in [Phase 5](#phase-5--bring-up-new-stack--verify-metric-pipeline) step 5 after the SQL probe.
- `pg_stat_bgwriter_checkpoints_timed_total`, `pg_stat_bgwriter_checkpoints_req_total`, `pg_stat_bgwriter_checkpoint_write_time_total`, `pg_stat_bgwriter_checkpoint_sync_time_total` are all **present** in `checkpoint.before`. Four entries. Same rule as above — presence required, non-zero not required at the idle baseline.
- Replication slot lag is acceptable (<50 MB) and slot is `active=t`.

**On failure.**
- If the slot is inactive or lag is high: do **not** proceed. Investigate Debezium first. Phase 3 cannot drain a slot whose consumer is unhealthy.

---

## Phase 1 — Method decision

**Status: DECIDED — `pg_upgrade --link` (Phase 4a).** This phase is documentation-only at execution time; agents read it for context, do not re-litigate it.

**Assigned agent.** None (decision frozen). If a future condition requires reopening — e.g. `pgautoupgrade` image is unavailable for the host architecture — escalate to the human owner; do not silently switch to Phase 4b.

**Goal (historical).** Pick `pg_upgrade --link` (Phase 4a) or `pg_dumpall + restore` (Phase 4b). Document the choice in the PR description for Phase 2. **Do not skip — they have different code-change footprints in Phase 2.**

| Aspect | `pg_upgrade --link` | `pg_dumpall + restore` |
|---|---|---|
| Speed on this dataset | Seconds (hard-link rename) | Seconds-to-minutes (small dataset; would be hours at production scale) |
| Disk usage | ≈0 extra (hard links) | ≈ 2× during overlap |
| Planner statistics preserved | **Yes** (PG18 feature, retroactive to PG14 sources) | **No** — empty `pg_stats` after restore until first ANALYZE |
| `pg_stat_statements` history | Lost (shared-memory only) | Lost (same) |
| Cumulative-stats counters (`pg_stat_database`, etc.) | Reset | Reset |
| Logical replication slots preserved | **No** (requires source ≥ PG17; we are PG16) | No |
| Rollback after new cluster starts writing | **Impossible** (hard links shared; old cluster is corrupted on first new-cluster write) | Possible (old volume untouched) |
| Docker volume layout migration | Required (one-time `data/` → `16/docker/` rename) | Avoided (fresh PG18 volume) |
| Container complexity | High — needs a transient container with both PG16 and PG18 binaries (e.g. `pgautoupgrade/pgautoupgrade:18-trixie`) | Low — two `docker compose` invocations |
| Production realism | High — same approach used in production | Low — production at scale cannot afford the dump time |

**Decision (locked in 2026-04-30).** `pg_upgrade --link` (Phase 4a). Rationale captured for the audit trail:

- This is a **learning project**; the local stack should mirror production realism, not optimize for local convenience.
- `--link` exercises the same upgrade primitive used in production — running it locally is the only way to surface the volume-layout footguns and the pg_upgrade container plumbing **before** they appear in a real outage window. A `dumpall` rehearsal would teach almost nothing transferable.
- Planner statistics are preserved (PG18 feature, retroactive to PG14 sources) — saves an `ANALYZE` and avoids a brief post-upgrade plan-quality dip.
- The rollback constraint (impossible after the new cluster has accepted writes) is mitigated by the mandatory `pg_dumpall` safety net taken in [Phase 3](#phase-3--drain--backup-cluster-state-touching-immediately-precedes-upgrade), which the rest of the plan already assumes.

**Phase 4b retained as documented fallback only.** Do not execute Phase 4b without first reopening this decision and getting human sign-off — silently flipping methods would invalidate the rollback assumptions in the [Rollback procedures](#rollback-procedures) section.

**Acceptance criterion for this phase.**
The PR description for Phase 2 cites this section verbatim and includes the line `Method: --link (decided 2026-04-30)`. No tooling change in this phase.

---

## Phase 2 — Code & config changes (single PR, lands but does not run)

**Status (2026-05-01): EXECUTED — both tracks landed on `feat/postgres-migration`; PR not yet merged (gated on Phase 7 per the runbook).**
- **Track 2.0 (Java-side compat audit, `java-backend-architect`)** — commit `734cc46 chore(test): bump Testcontainers postgres image to 18`. `AbstractContainerTest` now pins `postgres:18`. Dependency-version audit recorded in `PR_DESCRIPTION.md`: PostgreSQL JDBC driver 42.7.9 (Spring Boot BOM, ≥ 42.7.4 required for PG18 wire protocol), Flyway 12.0.1 (PG18-aware), jOOQ 3.19.29 (PG18 dialect detection). jOOQ regen deferred per the plan default — empirically verified: regen against a disposable PG18 sidecar produced a 2-file diff (`OUTBOX_EVENT_ID_OCCURRED_AT_KEY1` → `_KEY`) that is **state-dependent on cluster history, not a PG18 catalog change**, so regenerating now would drift from the post-`--link` catalog (which preserves `_KEY1` bit-for-bit). Forward-compat smoke test against the running PG16 cluster passed.
- **Track 2.1–2.8 (Infra/observability, `general-purpose`)** — single commit on the same branch. Six files modified: `docker/postgres/Dockerfile`, `docker-compose.local.yml`, `docker/sql-exporter/wal-io.collector.yml`, `docker/sql-exporter/replication.collector.yml`, `docker/prometheus/rules/db.rules.yml`, `docker/grafana/dashboards/db-server-health.json`. Sub-phases 2.3 (Linux overlay) and 2.8 (README) confirmed no-ops in this repo's state — Linux overlay only sets `extra_hosts`; README has no PG-major-version pin to bump.
- **Verification gates — all green.** `mvn clean install`: 228 tests, 0 failures, BUILD SUCCESS in ~38s (load-bearing repository tests against the PG18 Testcontainer included). `docker buildx build docker/postgres/`: succeeded; `postgresql-18-cron` PGDG package available on `postgres:18-trixie`; image `wealthpay-postgres-local` exported. `docker run … promtool check rules /rules/db.rules.yml`: SUCCESS, 8 rules found (down from 9 — `pg_replication_slot:inactive_age_seconds` recording rule removed, replaced by SQL-side `pg_replication_slots_inactive_since_seconds` gauge).
- **Metric inventory after the upgrade fires** (full list in `PR_DESCRIPTION.md`, "Metrics that change" section): four `pg_stat_bgwriter_checkpoint*` series → `pg_stat_checkpointer_*` (postgres-exporter built-in PG17+ rename); one new `pg_replication_slots_inactive_since_seconds`; one removed recording rule; four `pg_wal_stat_*` series with **names preserved** but source moved to `pg_stat_io WHERE object='wal' AND backend_type='walwriter'` (this filter is the only load-bearing assumption flagged for Phase 2.9 to ground-truth against live PG18).
- **Watch-items for Phase 2.9.** (1) Confirm `backend_type='walwriter'` is the right `pg_stat_io` row attribution on PG18 GA — the rehearsal's exporter-grep step (`curl localhost:9399/metrics | grep pg_wal_stat`) is the natural check. If wrong, all four `pg_wal_stat_*` metrics will go silent and the filter must be widened. (2) The actual Docker volume on this host is `wealthpay_pg_data` (compose project-prefixed), not bare `pg_data`. Phase 2.9 step 0 already mandates this re-verify; flagged here so the operator doesn't rediscover it from a missing-volume error.
- **Operator safety note.** The Phase 2.2a volume-mount-path edit is inert until somebody runs `up -d`. The branch must NOT be brought up via `./scripts/infra.sh up -d` until Phase 4a step 1 has migrated the on-disk layout — otherwise PG18 finds an empty `/var/lib/postgresql/18/docker` and either refuses to start or `initdb`s a fresh empty cluster while the PG16 data sits dormant at the volume root. **Post-merge enforcement:** `scripts/infra.sh` now refuses to bring the stack up when it detects a pre-PG18 `pg_data` layout (`PG_VERSION` at volume root) — fail-fast guard with an actionable error pointing back at this runbook. Future major-version cutovers (PG19+) that move the mount path again should refresh the markers in that guard; the principle "refuse layouts the current stack can't safely consume" is permanent.
- **Next phase:** [Phase 2.9](#phase-29--build-and-rehearse-the-pg-upgrader-image-blocking-gate-before-phase-3) — `pg-upgrader` image rehearsal. Human approval required before Phase 3.

**Goal.** Land **all** file changes the upgrade requires in one reviewable PR on a feature branch. The branch is **not merged to `main` until Phase 7 passes** — but it must be ready before Phase 4 starts so the new stack comes up correctly.

**Assigned agents.** Phase 2 splits into two tracks owned by different agents in parallel; both must finish before the PR is reviewable. Track them as separate commits on the same feature branch.

| Sub-phase | Track | Assigned agent | Why |
|---|---|---|---|
| 2.0 | Java-side compatibility audit | **`java-backend-architect`** | Touches `pom.xml`, jOOQ codegen, `AbstractContainerTest`, JDBC driver — all wheelhouse |
| 2.1–2.8 | Infrastructure / observability config | **`general-purpose`** | YAML, JSON, Bash, Dockerfile — not Java |

**Total scope.** ~3 hours including build verification (Java track ~1h, infra track ~2h, can run in parallel).

**Inputs.** Phase 1 decision (`--link`), the inventory from Phase 0, the fix recipes in [`docs/postgres-upgrade.md`](postgres-upgrade.md).

**Files to change** (paths are relative to repo root):

### 2.0 Java-side compatibility audit (`java-backend-architect`, blocks 2.1–2.8 from being merged)

**Why this phase exists.** The infrastructure changes in 2.1–2.8 will silently land a stack where the **runtime** speaks PG18 but the **integration test suite** still spins up `postgres:16` (line 14 of [`AbstractContainerTest.java`](../src/test/java/org/girardsimon/wealthpay/account/infrastructure/db/repository/AbstractContainerTest.java) hard-codes the image tag). Worse, the build won't fail — Testcontainers is happy to run tests against an older PG, so a regression introduced by a PG18-specific feature would only surface in production. This sub-phase closes that gap and audits the rest of the JVM dependency chain in the same pass.

**Steps.**

1. **Pin Testcontainers to PG18.** Edit [`src/test/java/org/girardsimon/wealthpay/account/infrastructure/db/repository/AbstractContainerTest.java`](../src/test/java/org/girardsimon/wealthpay/account/infrastructure/db/repository/AbstractContainerTest.java) line 14:
   ```diff
   -      new PostgreSQLContainer("postgres:16")
   +      new PostgreSQLContainer("postgres:18")
   ```
   The Testcontainers image must match the runtime PostgreSQL **major version**. Earlier wording said the tag must match "exactly", but the runtime uses `postgres:18-trixie` (because the local Dockerfile builds on it for pg_cron) while the Testcontainers image is the bare upstream `postgres:18` (no pg_cron, faster CI pull). That mismatch is intentional and correct — the integration tests don't exercise pg_cron, and forcing them onto `postgres:18-trixie` would just slow down CI without testing anything new. The contract is: same major version (PG18 ↔ PG18), not byte-identical tags.

   **Note:** [`AbstractContainerTest.java`](../src/test/java/org/girardsimon/wealthpay/account/infrastructure/db/repository/AbstractContainerTest.java) uses the stock `postgres:16` image, **not** the local custom Dockerfile. This is correct — the integration tests don't need pg_cron, and pulling the bare upstream image keeps CI fast. Do **not** switch this to `postgres:18-trixie` unless a test specifically depends on the pg_cron extension being present (none currently do).

2. **Audit `pom.xml` for PG18 compatibility.** For each library version pinned in [`pom.xml`](../pom.xml), verify against upstream changelogs that the pinned version supports PG18. The current pins:
   - **Spring Boot 4.0.2** (line 8) — Spring Boot 4.x is PG18-aware out of the box.
   - **Postgres JDBC driver** — version is inherited from Spring Boot's `spring-boot-dependencies` BOM; verify by running `mvn dependency:tree | grep postgresql` and checking the resolved version. Anything ≥ 42.7.4 supports PG18 wire-protocol features. If Spring Boot 4.0.2 resolves an older driver, **override the version explicitly** in `pom.xml`.
   - **Flyway 12.0.1** (line 42) — Flyway ≥ 11 includes PG18 detection; 12.0.1 is fine. No change.
   - **jOOQ 3.19.29** (line 44) — jOOQ 3.19+ recognizes PG18 in `PostgresDatabase` codegen and runtime dialect detection. Confirmed compatible; no change. (The runtime dialect is auto-detected from the JDBC connection metadata, so as long as the driver speaks PG18 the rest of the stack inherits.)
   - **Testcontainers** — version is also via Spring Boot BOM; verify ≥ 1.20.4 (which adds PG18 to its known-image list, mostly cosmetic — Testcontainers is image-agnostic).

   The audit deliverable is a short table in the PR description with each library, the resolved version, and a "PG18 OK" or "needs override" verdict.

3. **Decide: regenerate jOOQ classes from a PG18 schema?**

   The committed jOOQ generated classes under [`src/main/generated-jooq/`](../src/main/generated-jooq/) were produced against PG16's `information_schema` and `pg_catalog`. PG18 changes some catalog views (e.g., the new columns on `pg_replication_slots` and `pg_stat_io`) but **none of them are touched by the application's queries** — those are observability concerns, surfaced through sql-exporter, not through jOOQ.

   **Default: do not regenerate.** The committed PG16-generated classes will continue to work against PG18 because the `account.*` schema is unchanged by the upgrade and that's all jOOQ codegen reads. Regeneration is a behaviour-equivalent diff that adds noise to the PR.

   **Override condition:** if a future migration adds a column or table that uses a PG18-only feature (UUIDv7 default, virtual generated column, etc.), regenerate at that point. A regen-now sets a precedent that "every PG upgrade triggers a jOOQ regen" — which the architecture explicitly does not require.

   The decision goes in the PR description: "jOOQ regen: deferred — application schema unchanged."

4. **Build verification — run the full test suite against the PG18 testcontainer.** This is the load-bearing acceptance check; if it fails, the runtime stack would also fail.
   ```bash
   mvn clean install
   ```
   Pay attention to:
   - **Repository tests** (`*RepositoryIntegrationTest`) — these exercise jOOQ against a live PG18 via Testcontainers. A failure here means the PG16-generated jOOQ classes have a real incompatibility with PG18 (overrides the default in step 3).
   - **`ArchitectureTests` and `HexagonalArchitectureTest`** — these are pure-JVM and not PG-dependent, but if they fail something else is wrong.
   - **`ContainerTest`-derived tests** — these spin up a fresh PG18 per test class. First-run will be slow (image pull); subsequent runs cached.
   - **Mutation testing (`mvn pitest:mutationCoverage`)** — not strictly required for this phase; defer to a follow-up if time-pressed.

5. **Manual smoke-test the dev profile.** With the test suite green, do one ad-hoc Spring Boot run against the **current** PG16 image to confirm nothing in the Java track broke against the still-running production version (the Java track lands on `main` before the runtime cutover):
   ```bash
   ./scripts/infra.sh up -d   # current PG16 stack
   mvn spring-boot:run        # should connect, Flyway should report nothing-to-do
   ```

### Verification (2.0 only)

- All integration tests pass against `postgres:18` Testcontainers image.
- `mvn dependency:tree | grep postgresql` shows a JDBC driver ≥ 42.7.4.
- Spring Boot starts cleanly against PG16 with the Phase 2.0 changes (forward-compat check, since Phase 2.0 lands before the runtime cutover).
- The PR description includes the dependency-version audit table and the jOOQ regeneration decision.

### Acceptance criterion for 2.0

A separate commit on the Phase 2 feature branch titled `chore(test): bump Testcontainers postgres image to 18` (or similar) — Java track owns this commit independently of the infra track's commits.

---

### 2.1 `docker/postgres/Dockerfile`

Bump base image and pg_cron package version. **Base flavor flips from `bookworm` (Debian 12) to `trixie` (Debian 13)** — see rationale below.

```diff
-FROM postgres:16-bookworm
+FROM postgres:18-trixie

 RUN apt-get update \
-    && apt-get install -y --no-install-recommends postgresql-16-cron \
+    && apt-get install -y --no-install-recommends postgresql-18-cron \
     && rm -rf /var/lib/apt/lists/*
```

The `postgresql-18-cron` package is published by `apt.postgresql.org` (PGDG) for both `bookworm-pgdg` and `trixie-pgdg` on `arm64` (verified 2026-05-01); no third-party source needed.

**Why trixie, not bookworm.** The project's original `docker-compose.local.yml` used bare `postgres:16` (a moving major-tag, glibc-flavor decided by upstream). The `postgres:16-bookworm` pin only landed at commit `bd3d365` *to make `postgresql-16-cron` install reliably* (PGDG packaging timing at the time). That tactical pin had a side-effect: the catalog had been initialized against a trixie-glibc base earlier, which is why `psql` currently emits `WARNING: database "wealthpay" has a collation version mismatch (DB 2.41 vs OS 2.36)` — `2.41` is glibc 2.41 (trixie), `2.36` is glibc 2.36 (bookworm). Going to `postgres:18-trixie` aligns the runtime glibc with the catalog's stamped version, resolves the warning *for the right reason* (rather than papering over it with `ALTER DATABASE … REFRESH COLLATION VERSION`), and matches the project's pre-bookworm convention. As a bonus, the upgrader image (`pgautoupgrade/pgautoupgrade:18-*`) is only published for trixie/debian/alpine — no bookworm — so trixie also aligns the source image with the upgrade tool, eliminating a glibc cross-image discrepancy during `pg_upgrade --link`.

### 2.2 `docker-compose.local.yml`

Three edits, one of which is the **breaking** PG18 image change.

**a. Volume mount path change** (PG18 PGDATA layout):
```diff
     volumes:
-      - pg_data:/var/lib/postgresql/data
+      - pg_data:/var/lib/postgresql
```
The PG18 image declares `VOLUME /var/lib/postgresql` and sets `PGDATA=/var/lib/postgresql/18/docker`. Mounting at the parent enables future `pg_upgrade --link` between major versions on a single named volume (PG19 cluster would land in `/var/lib/postgresql/19/docker` alongside).

**b. Bootstrap container image bump:**
```diff
   postgres-bootstrap:
-    image: postgres:16
+    image: postgres:18
```
The bootstrap container only runs `psql` against the primary; bumping it keeps the wire-protocol client in lockstep with the server.

**c. Add the `--collector.stat-checkpointer` flag to postgres-exporter** (PG17 hazard fix from [postgres-upgrade.md §PG17](postgres-upgrade.md#pg17--pg_stat_bgwriter-columns-relocate-to-pg_stat_checkpointer)):
```diff
     command:
       - "--collector.stat_statements"
       - "--collector.stat_statements.include_query"
+      - "--collector.stat_checkpointer"
```
This emits the four `pg_stat_checkpointer_*` series. The four `pg_stat_bgwriter_checkpoint*` series stop being emitted on PG17+ regardless of this flag — Phase 5 updates the alert rule and dashboard panel that reference the old names.

### 2.3 `docker-compose.local.linux.yml`

No change required. The Linux overlay only sets `extra_hosts` on Prometheus.

### 2.4 `docker/sql-exporter/wal-io.collector.yml`

Apply the PG18 fix recipe from [postgres-upgrade.md §PG18](postgres-upgrade.md#pg18--pg_stat_wal-columns-relocate-to-pg_stat_io). Concretely:

- Reduce the `pg_stat_wal` query to the four columns that remain (`wal_records`, `wal_fpi`, `wal_bytes`, `wal_buffers_full`).
- Add a new `pg_stat_io_wal` query that selects `writes`, `fsyncs`, `write_time`, `fsync_time` from `pg_stat_io WHERE object = 'wal'`.
- Re-route the four affected `metric_name`s (`pg_wal_stat_write_total`, `pg_wal_stat_sync_total`, `pg_wal_stat_write_time_milliseconds_total`, `pg_wal_stat_sync_time_milliseconds_total`) to `query_ref: pg_stat_io_wal`.
- **Critical:** verify the `backend_type` filter in the new query against live PG18 output **before** committing. From PG18 release notes the WAL writer's I/O is attributed to `backend_type='walwriter'`, but the WAL receiver appears as `'walreceiver'`. This stack runs no replicas, so `walreceiver` rows are absent — but the filter must still scope to a single row to keep the metric labels-free.

The four metrics' names are **preserved** by this rewiring, which is the contract that keeps R2 satisfied and dashboard panels #8–#11 in `db-server-health.json` rendering without panel-side edits.

### 2.5 `docker/sql-exporter/replication.collector.yml`

Replace the recording-rule workaround with a SQL-side `inactive_since` column. PG18 (and PG17+) has the column; the recording rule was a PG16 fallback explicitly flagged in the file header as the "PG17 migration step".

```diff
       query: |
         WITH s AS (SELECT pg_current_wal_lsn() AS cur_lsn)
         SELECT
           slot_name,
           slot_type,
+          EXTRACT(epoch FROM inactive_since)::double precision AS inactive_since_seconds,
           pg_wal_lsn_diff(
             s.cur_lsn,
             COALESCE(restart_lsn, s.cur_lsn)
           )::double precision AS wal_retention_bytes
         FROM pg_replication_slots, s
```

Add one new metric definition:
```yaml
  - metric_name: pg_replication_slots_inactive_since_seconds
    type: gauge
    help: "Unix timestamp (seconds) when the slot last became inactive. NULL when active — series is absent in that case. Compute `time() - pg_replication_slots_inactive_since_seconds` for inactivity age."
    key_labels: [slot_name, slot_type]
    values: [inactive_since_seconds]
    query_ref: pg_replication_slots
```

> **`invalidation_reason` is intentionally out of scope for this migration.** An earlier draft proposed a `pg_replication_slots_invalidation_reason_info` metric with `values: [invalidation_reason]` and asked the agent to choose between two implementations of a contract that sql-exporter rejects (it requires a numeric value column; `invalidation_reason` is text). That is not agent-safe — a runbook should not include a broken implementation and ask the agent to decide. The metric is removed entirely from this migration and tracked as a follow-up. If/when added, the correct shape uses a separate query that selects `1::double precision AS present` from `pg_replication_slots WHERE invalidation_reason IS NOT NULL` with `invalidation_reason` as a key label, not as the value.

### 2.6 `docker/prometheus/rules/db.rules.yml`

Two edits.

**a. Remove the recording rule** that derived `pg_replication_slot:inactive_age_seconds` from the `pg_replication_slots_active` series. The new SQL-side metric replaces it.

**b. Update `ReplicationSlotInactive` alert** to reference the new metric directly:
```diff
       - alert: ReplicationSlotInactive
-        expr: pg_replication_slot:inactive_age_seconds > 300
+        expr: time() - pg_replication_slots_inactive_since_seconds > 300
         for: 1m
```
The semantic is unchanged; the implementation is now a single SQL column instead of a Prometheus subquery. ADR-008 also references the recording rule explicitly — see Phase 8.

**c. Update `DbForcedCheckpointRatioHigh`** to reference the new `pg_stat_checkpointer_*` metrics:
```diff
         expr: |
-          rate(pg_stat_bgwriter_checkpoints_req_total[10m])
+          rate(pg_stat_checkpointer_num_requested_total[10m])
           /
           (
-            rate(pg_stat_bgwriter_checkpoints_req_total[10m])
-            + rate(pg_stat_bgwriter_checkpoints_timed_total[10m])
+            rate(pg_stat_checkpointer_num_requested_total[10m])
+            + rate(pg_stat_checkpointer_num_timed_total[10m])
             > 0
           )
           > 0.3
```

### 2.7 `docker/grafana/dashboards/db-server-health.json`

Update panels #12 and #13 (per the postgres-upgrade.md fix recipe) to reference the new `pg_stat_checkpointer_*` metric names. The PromQL replacements:

| Old | New |
|---|---|
| `pg_stat_bgwriter_checkpoints_timed_total` | `pg_stat_checkpointer_num_timed_total` |
| `pg_stat_bgwriter_checkpoints_req_total` | `pg_stat_checkpointer_num_requested_total` |
| `pg_stat_bgwriter_checkpoint_write_time_total` | `pg_stat_checkpointer_write_time_total` |
| `pg_stat_bgwriter_checkpoint_sync_time_total` | `pg_stat_checkpointer_sync_time_total` |

Apply the same find-and-replace across `db-query-performance.json` and `account-service.json` defensively. Most likely no occurrences in the latter two; verify.

Panels #8–#11 in `db-server-health.json` reference `pg_wal_stat_*`, which Phase 2.4 preserves by name, so **no JSON edit there**.

### 2.8 README

Update the table of versions in [`README.md`](../README.md) (search for "Postgres 16") and any "we run PG16" sentence in passing.

### Verification (Phase 2 only — does not require running the upgrade yet)

1. `mvn clean install` succeeds. The build does not require a running database (jOOQ sources are committed).
2. `docker buildx build docker/postgres/` succeeds for the new Dockerfile (catches the case where `postgresql-18-cron` is unavailable in the apt repository configured by the base image).
3. `yamllint` (if installed) passes on the modified `*.yml` files.
4. Promtool validates the rules:
   ```bash
   docker run --rm -v "$PWD/docker/prometheus/rules:/rules" prom/prometheus:v3.10.0 \
     promtool check rules /rules/db.rules.yml
   ```

### Acceptance criterion

PR description includes:
- The `Method: --link | dumpall` line from Phase 1.
- The output of `docker buildx build docker/postgres/` showing a successful PG18 image build.
- The `promtool check rules` output showing 0 errors.
- A bullet list of every metric name that changes after upgrade (the four bgwriter→checkpointer renames, plus the recording-rule removal).

---

## Phase 2.9 — Build and rehearse the `pg-upgrader` image (BLOCKING gate before Phase 3)

**Status (2026-05-01): EXECUTED.** Rehearsal succeeded against a disposable copy of `wealthpay_pg_data`. `pg_upgrade --check` exit 0; `pg_upgrade --link` exit 0; rehearsed PG18.3 cluster booted via `wealthpay-postgres-local` with the production GUC set, `pg_cron` preloaded, all six contract-table row counts identical to the Phase 0 baseline (`event_store=2 124 327`, `account_balance_view=718 004`, `account_snapshot=35`, `processed_transactions=1 159 354`, `processed_reservations=248 088`, `outbox_cleanup_log=19`), and the pre-existing collation-version warning (catalog 2.41 vs OS 2.36) resolved on the trixie-based PG18 image as predicted in Phase 0. The real `wealthpay_pg_data` volume was never mounted read-write; every read used `:ro` (load-bearing safety) and the before/after `ls -la` snapshots are identical except for an alpine-container `/v/..` mtime artefact. Logs preserved at `~/wealthpay-pg-upgrade-baseline/pg_upgrade.link.rehearsal.log`, `~/wealthpay-pg-upgrade-baseline/pg16-rehearsal-recovery.log`; full rehearsal report at `~/wealthpay-pg-upgrade-baseline/phase-2.9-rehearsal.md`.

- **Image deliverable.** `docker/pg-upgrader/Dockerfile` committed; built locally as `wealthpay-pg-upgrader:18` (base `postgres:18-trixie`, packages `postgresql-16` 16.13-1.pgdg13+1, `postgresql-16-cron` 1.6.7-2.pgdg13+1, `postgresql-18-cron`, `locales-all`). Both `/usr/lib/postgresql/{16,18}/lib/pg_cron.so` present; `en_US.utf8` locale (the source's `LC_COLLATE`) available. Phase 4a's `ghcr.io/<org>/pg-upgrader:18` placeholder should be substituted with `wealthpay-pg-upgrader:18` (or whatever the operator pushes to a registry).
- **Findings folded back into the runbook.** Six real configuration drifts were surfaced. Each was *recoverable* (none cross the `--link` cliff) but each adds friction the operator should not rediscover at execution time. The runbook now incorporates them inline so Phase 4a runs deterministically on the real volume:
  1. **Phase 4a step 1** — wrapper directories `/v/16` and `/v/16/docker` come out of the staging `mv` as `root:root` mode 0755; postgres requires the data dir owned by `postgres` (UID 999) with mode 0700. A `chown -R 999:999 /v/16 && chmod 0700 /v/16/docker` was added to the step.
  2. **Phase 4a step 2 (initdb)** — PG18's `initdb` enables data page checksums **by default**; the source has `Data page checksum version: 0`. The example now passes `--no-data-checksums` (and the comment is corrected — earlier wording suggested adding `--data-checksums` if checksums were on, but the PG18 default flip means we need `--no-data-checksums` when checksums are off).
  3. **Phase 4a step 2 (initdb)** — this cluster's superuser is `user`, not `postgres`. Without `--username=user`, `pg_upgrade --check` later fails on the new-cluster side with `role "user" does not exist`. The example now passes `--username=user`.
  4. **Phase 4a step 2 (`pg_upgrade --check`/`--link`)** — `pg_upgrade` starts both clusters internally without re-reading compose's `-c` flags. With `pg_cron` and `pg_stat_statements` only set via compose `command:` (not persisted in `postgresql.conf`/`postgresql.auto.conf`), the source startup fails (`logical replication slot "debezium" exists, but wal_level < logical`) and the target startup fails (`pg_cron` not loaded). Both invocations now pass `--old-options="-c shared_preload_libraries=pg_cron,pg_stat_statements -c cron.database_name=wealthpay"` and the same `--new-options=...`, plus `--username=user`. With these, the source-side checks pass cleanly *because Phase 3 step 6 has dropped the slot*.
  5. **Phase 3 step 6 (slot drop)** — previously framed as "defence in depth" because today PG16-source slots aren't preserved by `pg_upgrade` regardless. The rehearsal proved it's actually **required** *given the conventions on this cluster* (see (4) above): without the slot drop, `pg_upgrade --check` errors out on the source side. Commentary updated.
  6. **Phase 5 post-boot housekeeping** — `pg_upgrade` emits an `update_extensions.sql` that wants `ALTER EXTENSION pg_stat_statements UPDATE` (the bundled version moves 1.10 → 1.12 between PG16 and PG18; `pg_cron` is 1.6 in both, no change). Added to Phase 5's post-boot steps.
- **Watch-items deferred to Phase 5.** Phase 2 flagged two checks for Phase 2.9 — (i) confirm `backend_type='walwriter'` is the right `pg_stat_io` row attribution on PG18 GA via `curl localhost:9399/metrics | grep pg_wal_stat`; (ii) confirm the volume name. (ii) was confirmed in Phase 2.9 step 0 (`wealthpay_pg_data`). (i) was **not** exercised in Phase 2.9 because the rehearsal step 7 boots only the postgres container, not the full compose stack including sql-exporter — so the exporter-grep step has no exporter to grep. Phase 5 step 1 (full stack up against the upgraded real volume) is the natural place; if `walwriter` is the wrong attribution there, all four `pg_wal_stat_*` series go silent and the filter must be widened per `postgres-upgrade.md §PG18`.
- **Real volume state at end of Phase 2.9.** Untouched. Live PG16 still serving on port 5432 from `wealthpay_pg_data`. Rehearsal volume `wealthpay_pg_data_rehearsal` destroyed in step 8.
- **Next phase:** [Phase 3](#phase-3--drain--backup-cluster-state-touching-immediately-precedes-upgrade) — drain & safety-net dump. Human approval of this sign-off is required before Phase 3 starts.

**Assigned agent.** Agent prepares the Dockerfile + rehearsal harness and reports results; **human reviews and signs off before Phase 3 fires.** The image artefact itself is real engineering work outside the scope of this runbook — what this phase does is *gate* Phase 3 on that work having been done and rehearsed against a disposable copy of the real volume.

**Goal.** Convert the `--link` upgrade from "a command in a runbook against a placeholder image" into "a tested artefact with a known exit code on a known input". The whole `--link` cliff is meaningless if the upgrader image cannot start — and we cannot find that out for the first time on the production-equivalent volume.

**Why this phase exists.** Earlier drafts referenced `ghcr.io/<org>/pg-upgrader:18` as a placeholder. Phase 4a is not safe if the upgrader image is still a placeholder when Phase 3 starts taking the safety-net dump and stopping the cluster. Rehearsing on a disposable copy proves the image runs end-to-end without crossing the irreversibility cliff on the real volume.

**Scope.** ~2 hours including image build (lower if a published artefact is reused; higher if pg_cron compatibility issues surface).

**Pre-requisite.** Phase 2 PR is on the feature branch (so the new compose layout is available locally). Phase 0 baseline files exist for reference.

**Steps.**

0. **Volume-name preflight (also applies to Phase 4a step 1).** Both Phase 2.9 and Phase 4a operate on a Docker volume by the literal name `pg_data`. Compose may prefix named volumes with the project name (e.g. `wealthpay_pg_data`) unless the compose file pins `name:` on the volume. Confirm before any volume operation:
   ```bash
   docker compose -f docker-compose.local.yml config --volumes
   docker volume inspect pg_data >/dev/null 2>&1 \
     || { echo "ABORT: volume 'pg_data' not found — check `docker volume ls` for the actual name (likely 'wealthpay_pg_data')." >&2; exit 1; }
   ```
   If the inspect fails, **stop and resolve the actual volume name** before continuing. Substitute the real name everywhere `pg_data` appears in this phase and Phase 4a — do not improvise.

1. **Build the upgrader image.** Two acceptable shapes:
   - Hand-rolled, based on `postgres:18-trixie` with `apt-get install postgresql-16 postgresql-16-cron postgresql-18-cron`. This is the documented default — the build is reproducible from the Dockerfile alone and the binaries are PGDG-published. **Trixie matches the main runtime image (Phase 2.1) and the upstream `pgautoupgrade` PG18 line — keeping glibc identical across all three avoids cross-image collation-version surprises during `pg_upgrade --link`.**
   - `pgautoupgrade/pgautoupgrade:18-trixie`. Allowed if (and only if) its README documents `--link` mode for the version pulled, and the entrypoint behaviour matches what Phase 4a expects. Note: the `:18-bookworm` tag does not exist on Docker Hub — `:18-trixie` / `:18-debian` / `:18-alpine` are the published flavors as of 2026-04-26.
2. **Create a disposable rehearsal volume from the real one — mount the real volume read-only** so the cp cannot accidentally write back. This makes the "real volume not touched" claim true *by construction*, not by inspection after the fact:
   ```bash
   docker volume create pg_data_rehearsal
   docker run --rm -v pg_data:/from:ro -v pg_data_rehearsal:/to alpine sh -c 'cp -a /from/. /to/'
   ```
   Apply the same `:ro` mount mode to any subsequent rehearsal command that reads from the real volume — cheap safety win, removes a class of "rehearsal accidentally mutated production" bugs.
3. **Run the same volume-layout migration** from Phase 4a step 1 against the rehearsal volume (substitute `pg_data_rehearsal` for `pg_data`). Confirm pre-checks pass and no data is lost.

   **Rehearsal-only prep — only if you cloned a *running* PG16.** Phase 4a runs against a cluster that Phase 3 step 7 has cleanly stopped, so the source datadir at execution time is already in `Database cluster state: shut down` and has no `postmaster.pid` or active replication slots in the way. The Phase 2.9 clone of a running PG16 is in `Database cluster state: in production` with a stale `postmaster.pid` and the live `debezium` slot — `pg_upgrade --check` rejects all three. Synthesize the post-Phase-3 state on the clone before step 4: remove the stale pid (`rm -f /v/16/docker/postmaster.pid`), boot the clone via `pg_ctl` with the same compose GUCs (`-c wal_level=logical -c max_replication_slots=10 -c max_wal_senders=12 -c shared_preload_libraries=pg_cron,pg_stat_statements -c cron.database_name=wealthpay`), drop the slot (`SELECT pg_drop_replication_slot('debezium')`), and `pg_ctl stop -m fast`. Confirm `pg_controldata` reports `shut down` before continuing. (If you cloned an already-stopped cluster — e.g. snapshotted after Phase 3 — skip this prep entirely.)
4. **Initialize the PG18 target data directory — with locale and encoding extracted from the source.** `pg_upgrade` requires an *existing, initialized* new cluster *with matching encoding and locale*; if any of these diverge between source and target, `pg_upgrade --check` fails. The hand-rolled image needs an explicit `initdb`; only `pgautoupgrade` does this for you (and only if its entrypoint actually runs). Skip this step **only** if you chose Option A in step 1 *and* verified its README states the entrypoint runs `initdb`.

   **First, extract the source locale/encoding** so the values driving `initdb` are not hardcoded — the source cluster may not be `C.UTF-8`:
   ```bash
   docker run --rm -v pg_data_rehearsal:/v ghcr.io/<org>/pg-upgrader:18 \
     /usr/lib/postgresql/16/bin/pg_controldata /v/16/docker | \
     grep -E 'LC_COLLATE|LC_CTYPE|Database block size|Data page checksum'
   # Sample output:
   #   LC_COLLATE: en_US.UTF-8
   #   LC_CTYPE:   en_US.UTF-8
   #   Database block size: 8192
   #   Data page checksum version: 0
   ```
   **STOP if** the source `LC_COLLATE` / `LC_CTYPE` is a locale not present in the PG18 image (`docker run --rm ghcr.io/<org>/pg-upgrader:18 locale -a` to enumerate). The image must include the source locale, otherwise `initdb` cannot reproduce it. Fix in the upgrader Dockerfile (`apt-get install locales-all` or generate the specific locale via `localedef`); do not improvise an "almost-matching" locale.

   **Then run `initdb`** with the extracted values. The example below uses `en_US.utf8` — substitute whatever `pg_controldata` reported. The two non-default flags below are load-bearing on this cluster (this is the same flag set Phase 4a step 2 uses; see Phase 2.9 status block for the rationale):
   - `--no-data-checksums` — PG18's `initdb` default flipped to ON; source has `Data page checksum version: 0`. They must match or `pg_upgrade --check` fails. If your source ever has `Data page checksum version: 1`, drop this flag and pass `--data-checksums` instead.
   - `--username=user` — cluster's superuser is `user`, not `postgres`. Without this, `pg_upgrade --check` fails later with `role "user" does not exist` on the new-cluster side.
   ```bash
   docker run --rm -v pg_data_rehearsal:/var/lib/postgresql ghcr.io/<org>/pg-upgrader:18 \
     bash -lc '
       set -euo pipefail
       rm -rf /var/lib/postgresql/18/docker
       mkdir -p /var/lib/postgresql/18/docker
       chown -R postgres:postgres /var/lib/postgresql/18
       chmod 0700 /var/lib/postgresql/18/docker
       # Substitute the locale below with the value extracted from pg_controldata above.
       su postgres -c "/usr/lib/postgresql/18/bin/initdb \
         -D /var/lib/postgresql/18/docker \
         --encoding=UTF8 \
         --locale=en_US.utf8 \
         --no-data-checksums \
         --username=user"
     '
   ```
5. **Run `pg_upgrade --check`** (not `--link`) first. This is non-mutating and proves the upgrader image can read both old and new clusters and produce an upgrade plan without surprises. Pass `--username=user` and the same `--old-options`/`--new-options` Phase 4a uses (see step 2 of that phase for the rationale — `pg_upgrade` starts both clusters internally without re-reading compose's `-c` flags, so `pg_cron` / `wal_level=logical` must be passed explicitly here):
   ```bash
   docker run --rm -v pg_data_rehearsal:/var/lib/postgresql ghcr.io/<org>/pg-upgrader:18 \
     /usr/lib/postgresql/18/bin/pg_upgrade --check \
       --old-bindir=/usr/lib/postgresql/16/bin \
       --new-bindir=/usr/lib/postgresql/18/bin \
       --old-datadir=/var/lib/postgresql/16/docker \
       --new-datadir=/var/lib/postgresql/18/docker \
       --username=user \
       --old-options="-c shared_preload_libraries=pg_cron,pg_stat_statements -c cron.database_name=wealthpay" \
       --new-options="-c shared_preload_libraries=pg_cron,pg_stat_statements -c cron.database_name=wealthpay"
   ```
6. **Run `pg_upgrade --link`** against the rehearsal volume. Capture stdout + stderr + exit status to a log file alongside the baseline.
7. **Start the rehearsed PG18 cluster using the same custom image as runtime.** Bare `postgres:18-trixie` would not match the actual Phase 4a startup path: the runtime image is built from `docker/postgres/Dockerfile` (PGDG `postgres:18-trixie` + `postgresql-18-cron`), and runtime startup uses the compose `command:` block to set `shared_preload_libraries=pg_cron,pg_stat_statements` and the `track_*_io_timing` GUCs. Rehearsing with the bare image proves the wrong thing — pg_cron's preload would not run and you'd miss any pg_cron-vs-PG18 incompatibility. Build the runtime image first if not already cached, then start the rehearsed cluster with the same arguments compose would pass:
   ```bash
   # Build the runtime image (no-op if already built):
   docker build -t wealthpay-postgres-local docker/postgres
   # Start the rehearsed cluster with the same shared_preload_libraries and GUCs as runtime:
   docker run --rm -d --name pg18-rehearsal \
     -v pg_data_rehearsal:/var/lib/postgresql \
     -e POSTGRES_USER=user \
     -e POSTGRES_PASSWORD=password \
     -e POSTGRES_DB=wealthpay \
     -e PGDATA=/var/lib/postgresql/18/docker \
     -p 15432:5432 \
     wealthpay-postgres-local \
     postgres \
       -c wal_level=logical \
       -c max_replication_slots=10 \
       -c max_wal_senders=12 \
       -c shared_preload_libraries=pg_cron,pg_stat_statements \
       -c cron.database_name=wealthpay \
       -c track_io_timing=on \
       -c track_wal_io_timing=on
   sleep 3   # let the cluster accept connections
   docker exec -T pg18-rehearsal psql -U user -d wealthpay -c "SELECT version();"
   docker exec -T pg18-rehearsal psql -U user -d wealthpay -c "SELECT count(*) FROM account.event_store;"
   docker exec -T pg18-rehearsal psql -U user -d wealthpay -c "SELECT extname, extversion FROM pg_extension;"
   docker exec -T pg18-rehearsal psql -U user -d wealthpay -c "SHOW shared_preload_libraries;"
   docker stop pg18-rehearsal
   ```
   `pg_cron` must appear in `pg_extension`, `SHOW shared_preload_libraries` must include `pg_cron`, and the upgrade must not have reset its catalog. Keep this command in lockstep with `docker-compose.local.yml`'s `command:` block — when the compose block changes, this rehearsal command must change with it.
8. **Destroy the rehearsal volume.** `docker volume rm pg_data_rehearsal`. Real `pg_data` is still untouched.

**Acceptance criteria.**
- The `pg-upgrader` image exists locally (and ideally is pushed to a registry so Phase 4a can pull rather than build).
- `pg_upgrade --check` exits 0 against the rehearsal volume.
- `pg_upgrade --link` exits 0 against the rehearsal volume.
- The rehearsed PG18 cluster starts, reports PG18, has the expected row count in `account.event_store`, and has `pg_cron` installed.
- The real `pg_data` volume was never mounted read-write by any command in this phase — the `:ro` mode on the cp source (step 2) makes accidental writes impossible at the kernel level. A top-level `docker run --rm -v pg_data:/v alpine ls -la /v` snapshot before/after Phase 2.9 should still match for sanity, but the read-only mount is the load-bearing safety, not the listing comparison (`ls -la` would not detect file-content drift even if it occurred).
- The pg_upgrade log is preserved alongside the Phase 0 baseline files.

**On failure.**
- `pg_upgrade --check` fails: do **not** proceed to step 5. Read the upgrade output for the specific incompatibility (most common: extension version mismatch, missing locale, datadir permission). Fix in the upgrader image, rebuild, re-rehearse. The real volume is still safe.
- `pg_upgrade --link` exits non-zero on the rehearsal: same recovery path; do not proceed to Phase 3. The whole point of rehearsing is to surface this failure before the real cluster is stopped.
- pg_cron is missing or broken on the upgraded cluster: this is a known PG version-bump hazard; verify `postgresql-18-cron` was installed in the upgrader image and that `shared_preload_libraries` survived the upgrade.

**Hard requirement.** Until this phase passes, **Phase 3 must not start.** No safety-net dump is taken, no connector is deleted, no cluster is stopped.

---

## Phase 3 — Drain & backup (cluster-state-touching, immediately precedes upgrade)

**Status (2026-05-01): EXECUTED.** Safety-net dump captured (`~/wealthpay-pg-upgrade-baseline/dumpall.before.sql`, 824 MB, 4 293 113 lines, 19 `CREATE TABLE` lines = 7 contract tables + 11 outbox daily partitions + 1 `flyway_schema_history`); the per-contract-table presence loop (Phase 3 acceptance criterion, run verbatim) emitted no `MISSING:` lines — every preserved table is in the dump. The `debezium` slot was inactive at kickoff (`restart_lsn=2/CDCFB6C8`, `confirmed_flush_lsn=2/CF4693D0`); explicit `pg_drop_replication_slot('debezium')` succeeded; post-drop count = 0. `./scripts/infra.sh stop` brought all previously-running containers to `Exited` (postgres `Exited (0)`, kafka brokers SIGTERM/SIGKILL is normal under compose stop); `wealthpay_pg_data` volume root contains no `postmaster.pid` (clean shutdown confirmed via `:ro` mount inspection). Stderr captured separately to `dumpall.before.stderr` — only the documented PG16-bookworm libc collation warnings (catalog 2.41 vs OS 2.36); the strict-diff target stays clean. Full execution log at `~/wealthpay-pg-upgrade-baseline/phase-3-execution.md`.

- **Acceptance criteria — final status.**
  - [x] `dumpall.before.sql` exists and is non-empty (>1 MB) — **824 MB**.
  - [x] Per-contract-table presence loop returns no `MISSING:` lines — **all 7 found**.
  - [N/A] Connector returns `404` from `GET /connectors/wealthpay-outbox-connector` — kafka-connect was in `Created` state at kickoff (never started this boot cycle); REST API unreachable, connector cannot be running. Connector config still sits in the Kafka `connect-configs` topic — harmless across the upgrade because Phase 6 (Path C) creates a renamed connector + `debezium_pg18` slot, but **must be DELETE'd before Phase 6 registers the new connector** (see § 4.2 carryover below).
  - [x] `pg_replication_slots WHERE slot_name IN ('debezium','debezium_pg18')` returned 0 rows post-drop, with the active-count assertion returning 0 — verified before stack stop.
  - [x] All previously-running containers in `Exited` state; `wealthpay-postgres` finished with `Exited (0)`; volume root has no `postmaster.pid`.
- **Pre-execution recovery (off-plan, recorded for the audit trail).** The Phase 2 commit (`a1438ff`) had already been merged on the working branch but the local stack had been restarted *before* Phase 3 — violating the plan's explicit "Phase 2 lands but does not run" gate. The Phase 2 mount-path edit left the postgres image's declared `VOLUME /var/lib/postgresql/data` uncovered by the bind, so Docker created an empty anonymous volume there and ran `initdb` against it; the original cluster on `wealthpay_pg_data` was masked but undamaged. Recovery: stop stack, remove the anonymous volume, *temporarily* revert `docker-compose.local.yml:13` and `docker/postgres/Dockerfile` back to PG16 to bring the live cluster back, take Phase 3's dump, drop the slot, stop the cluster, then `git checkout --` the recovery diffs so the working tree matches `HEAD` (`cc49cba`) again. Post-recovery integrity check: every contract-table row count matched Phase 0 baseline exactly (`event_store=2 124 327`, `account_balance_view=718 004`, `account_snapshot=35`, `processed_transactions=1 159 354`, `processed_reservations=248 088`, `outbox_cleanup_log=19`, `outbox=41 681` summed across daily partitions). Detailed recovery diff in `phase-3-execution.md` § 0; agents repeating Phase 3 should *not* take this off-plan path — only its existence is recorded so future operators recognize the failure mode if it recurs.
- **Carryovers folded into subsequent phases.**
  1. **Phase 4a step 1** — the volume root has a residual empty `/v/data` directory (link count 2) created by Docker as a bind-mount target during the off-plan boot. The Phase 4a step-1 staging move would otherwise sweep it into `/v/16/docker/data/`. Folded a `[ -d /v/data ] && rmdir /v/data 2>/dev/null || true` guard into the runbook (`rmdir` without `-r` refuses non-empty dirs — safe by construction).
  2. **Phase 5 / Phase 6** — `DELETE /connectors/wealthpay-outbox-connector` is owed before Phase 6 registers the new connector. Phase 3 step 5 was deferred (kafka-connect was not running); the residual config in `connect-configs` is harmless across the upgrade itself but will cause kafka-connect on the new PG18 cluster to attempt to start the legacy connector against a non-existent `debezium` slot.
  3. **Phase 4a step 5** — the locally-cached `wealthpay-postgres:latest` (`749ac7761e02`, built 2026-04-27) is still PG16; without `--build`, the cached PG16 binary will attempt to read a PG18-upgraded datadir and refuse to start with a catalog-version mismatch. Run `./scripts/infra.sh build postgres` (or `up -d --build postgres`) before the cluster start.
- **Real-volume state at end of Phase 3.** `wealthpay_pg_data` cluster `Database cluster state: shut down`, no `postmaster.pid`, `pg_replslot/` empty, working tree matches `HEAD` (`cc49cba`). Ready for Phase 4a step 1.
- **Next phase:** [Phase 4a](#phase-4a--execute-pg_upgrade---link-chosen-method) — `pg_upgrade --link`. Steps 1–3 are agent-owned; steps 5–6 are human-owned (irreversibility cliff at step 5).

**Assigned agent.** `general-purpose`. curl + psql + `pg_dumpall`; no Java code touched.

**Goal.** Bring Debezium to a clean stop with the slot fully drained, then take a no-questions-asked safety-net backup, then stop the cluster.

**Scope.** ~10 minutes.

**Steps.**

1. Stop the application (the only writer to `account.outbox`). With the writer gone, `pg_current_wal_lsn()` stops advancing on the application path — so the connector's `confirmed_flush_lsn` can actually catch up:
   ```bash
   # If running via mvn locally — Ctrl+C the mvn process. No-op if not running.
   ```
2. **Wait for the slot to drain *while the connector is still RUNNING*.** Order matters here: a paused Debezium connector stops consuming WAL, so `confirmed_flush_lsn` does not advance under pause — pausing first and then waiting deadlocks any non-trivial lag. Poll until `lag_bytes` is zero (or single-digit-byte checkpointer noise):
   ```bash
   # Connector status check — must be RUNNING, not PAUSED
   curl -sf http://localhost:8083/connectors/wealthpay-outbox-connector/status \
     | jq '.connector.state'   # expect: "RUNNING"
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT slot_name,
             pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn) AS lag_bytes
        FROM pg_replication_slots;"
   # Loop until lag_bytes is 0 (or single-digit-bytes — unflushed checkpointer noise is fine).
   ```
   If lag does not converge within ~5 minutes the consumer is unhealthy — STOP and investigate Debezium; the upgrade cannot proceed safely.
3. **Now** pause the connector. The drain is complete; pause prevents a surprise re-consumption attempt while the dump is being taken:
   ```bash
   curl -X PUT http://localhost:8083/connectors/wealthpay-outbox-connector/pause
   ```
4. **Take the safety-net dump regardless of upgrade method.** This is the only artefact that survives a cratered `--link` upgrade:
   ```bash
   docker compose exec -T postgres pg_dumpall -U user --clean --if-exists \
     > ~/wealthpay-pg-upgrade-baseline/dumpall.before.sql
   ls -lh ~/wealthpay-pg-upgrade-baseline/dumpall.before.sql
   ```
5. Stop the connector entirely (so the new cluster starts with no surprise reconnect attempts):
   ```bash
   curl -X DELETE http://localhost:8083/connectors/wealthpay-outbox-connector
   ```
6. **Drop the replication slots explicitly** (REQUIRED — promoted from "defence in depth" after the Phase 2.9 rehearsal). Connector deletion via the Kafka Connect REST API does **not** by default drop the underlying logical slot — the Debezium PostgreSQL connector's `drop.slots.on.stop` defaults to `false`. The Phase 2.9 rehearsal proved this step is *required* on this cluster, not just defensive: with the `debezium` slot still present at Phase 4a, `pg_upgrade`'s internal source-cluster startup falls back to `wal_level=replica` (because compose's `-c wal_level=logical` is not in the data-dir conf) and aborts with `logical replication slot "debezium" exists, but wal_level < logical`. `pg_upgrade --check` therefore exits non-zero — the upgrade is blocked until the slot is gone. The same explicit drop is also forward-compatible for a future re-run on a PG17+ source (where slots *would* be preserved by `pg_upgrade`) — it doesn't silently inherit a stale slot. Target both the legacy `debezium` name AND `debezium_pg18` (Phase 6 Path C creates the latter; if a previous attempt got that far before being rolled back, the slot may still exist):
   ```bash
   # Drop only inactive matching slots — pg_drop_replication_slot fails on active slots, by design.
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT slot_name, pg_drop_replication_slot(slot_name)
        FROM pg_replication_slots
       WHERE slot_name IN ('debezium', 'debezium_pg18')
         AND active = false;"
   # Then assert no matching slot is still ACTIVE — a still-active slot indicates a consumer is
   # somehow attached, which would corrupt the upgrade. Fail loud:
   docker compose exec -T postgres psql -U user -d wealthpay -tAc \
     "SELECT count(*) FROM pg_replication_slots
       WHERE slot_name IN ('debezium', 'debezium_pg18') AND active = true;" \
     | (read n; if [ "$n" -ne 0 ]; then echo "ABORT: $n target slot(s) still ACTIVE — investigate before stopping the stack." >&2; exit 1; fi)
   ```
   Idempotent — a no-op if neither slot exists, and a hard failure if any consumer is still attached (the connector DELETE in step 5 should have detached it).
7. Stop the entire stack:
   ```bash
   ./scripts/infra.sh stop
   ```
   `stop` rather than `down -v` — the volumes are intentionally retained for `--link` and as the rollback substrate.

### Acceptance criterion

- `dumpall.before.sql` exists and is non-empty (>1 MB).
- The dump contains a `CREATE TABLE` statement for every contract table listed in Phase 0 step 5 — this is what proves the dump is actually usable as the rollback substrate. The earlier draft compared `grep -c '^CREATE TABLE'` to Phase 0's row-count line count, but Phase 0 only captures row counts for the seven-table contract set, not a count of every table in the schema (Flyway history, outbox partitions, and any system-managed tables would inflate the dump's `CREATE TABLE` count and break the comparison). The honest check is per-table presence:
  ```bash
  for t in event_store account_balance_view account_snapshot processed_transactions processed_reservations outbox_cleanup_log outbox; do
    if ! grep -qE "^CREATE TABLE [^;]*account\.${t}\b" ~/wealthpay-pg-upgrade-baseline/dumpall.before.sql; then
      echo "MISSING: account.${t}" >&2
    fi
  done
  ```
  Output must be empty (every contract table found).
- The connector returns `404` from `GET /connectors/wealthpay-outbox-connector`.
- `SELECT count(*) FROM pg_replication_slots WHERE slot_name IN ('debezium', 'debezium_pg18')` returned 0 — verified before stop. (Both names targeted because Phase 6 Path C creates `debezium_pg18`; a partially-rolled-back previous attempt may have left it.)
- All compose containers are in `Exited` state.

---

## Phase 4a — Execute `pg_upgrade --link` (chosen method)

**Status (2026-05-01): EXECUTED.** Steps 1–3 ran on the agent side; steps 5–6 on the human side. `pg_upgrade --link` exited 0 (`Upgrade Complete`) against the real `wealthpay_pg_data` volume. PG18.3 came up healthy under `wealthpay-postgres:latest` (rebuilt from the post-`a1438ff` Dockerfile per the Phase 3 carryover); `SELECT version()` reports `PostgreSQL 18.3 (Debian 18.3-1.pgdg13+1) on aarch64-unknown-linux-gnu`. Step 1 (volume layout migration) reorganized the cluster from the volume root into `/v/16/docker/` with no data movement (metadata-only `mv`, checkpoint LSN `2/D0B57E88` unchanged before/after); step 2's `initdb` produced `/v/18/docker` with `--no-data-checksums` and `--username=user`; `pg_upgrade --check` then `--link` both exited 0; step 3 confirmed `/v/16/docker/global/pg_control` was renamed to `pg_control.old` (pg_upgrade's deliberate guard against PG16 restart) and `/v/18/docker/PG_VERSION=18`. Steps 5 and 6 ran on the human side with the rebuilt `wealthpay-postgres` image and the pg_hba.conf fixup folded in (see (5) below); `vacuumdb --all --analyze-in-stages --missing-stats-only` and `ALTER EXTENSION pg_stat_statements UPDATE` succeeded. Agent execution log at `~/wealthpay-pg-upgrade-baseline/phase-4a-execution.md`; pg_upgrade stdout at `~/wealthpay-pg-upgrade-baseline/pg_upgrade.link.real.log`; volume layout snapshot at `~/wealthpay-pg-upgrade-baseline/pg_data.after-4a.ls`.

- **Acceptance criteria — final status.**
  - [x] `pg_upgrade` exits 0 — confirmed (`EXITCODE=0`, `Upgrade Complete`).
  - [x] Post-upgrade `psql -c "SELECT version();"` reports PG18 — confirmed (`PostgreSQL 18.3`).
  - [N/A] R1 (data preservation) — owned by [Phase 4.5](#phase-45--post-upgrade-data-preservation-check-before-any-new-write). The strict diff against `~/wealthpay-pg-upgrade-baseline/row-counts.preserved.before` runs there, before any new write.
- **Findings folded back into the runbook.** Two new findings surfaced during real-volume execution that the rehearsal had not exercised. Each is *recoverable* but each adds friction the next operator should not rediscover.
  1. **Step 2 (`pg_upgrade --check` and `--link`)** — `pg_upgrade` requires a writable cwd for its log files (`pg_upgrade_internal.log`, `pg_upgrade_server.log`, `pg_upgrade_output.d/`). The upgrader image's default `WORKDIR` is not writable by the `postgres` user, so the bare `docker run` fails with `You must have read and write access in the current directory. Failure, exiting`. Both invocations now pass `--user postgres` and `--workdir <writable path>` (`/tmp` for `--check`, `/var/lib/postgresql/18/docker` for `--link` so the `pg_upgrade_output.d/` directory lands inside the new datadir where Phase 4a step 6 expects it).
  2. **Step 5 (right before `up -d`)** — `pg_upgrade` does not copy `pg_hba.conf` (its docs are explicit about this). The new cluster ships with the default initdb file, which is **localhost-only**. The PG16 cluster had `host all all all scram-sha-256` appended by docker-entrypoint.sh during PG16's first boot via `POSTGRES_HOST_AUTH_METHOD`. Without that line, sibling containers in the Docker network (Spring app, kafka-connect, postgres-exporter, sql-exporter) hit `FATAL: no pg_hba.conf entry for host …` after `up -d`. Phase 2.9's rehearsal missed this because its `psql` probes used localhost-only TCP, hitting the `127.0.0.1/32 trust` line that *does* exist in defaults. A pre-cliff `cp /v/16/docker/pg_hba.conf /v/18/docker/pg_hba.conf` is now folded into step 5.
- **Real-volume state at end of Phase 4a.** Stack up; PG18.3 healthy; `wealthpay-postgres:latest` rebuilt from the committed PG18 Dockerfile (digest changed from the pre-Phase-2 cached `749ac7761e02`); `/v/16/docker` is an inert hard-link husk (must NOT be started — `pg_control.old` enforces this); `/v/18/docker/update_extensions.sql` ran but did not fully reach the wealthpay DB (see below); `/v/18/docker/delete_old_cluster.sh` left in place — **do not run** until [Phase 7](#phase-7--final-verification-cdc-consistency--metrics-behaviour) passes and Phase 4.5 has signed off the data-preservation contract.
- **Post-execution finding (2026-05-02): `pg_stat_statements` extension stayed at 1.10 in the wealthpay DB.** Discovered ~4 hours post-cutover via postgres-exporter container logs: `ERROR: column pg_stat_statements.shared_blk_read_time does not exist` on every scrape (the column was renamed in pgss 1.11). Critically, `up{job="wealthpay-db"}=1` throughout — per-query SQL errors inside postgres-exporter v0.19.1 do not fail the HTTP scrape, so `InstanceDown` did not fire. Healed by `docker compose exec -T postgres psql -U user -d wealthpay -c "ALTER EXTENSION pg_stat_statements UPDATE;"` (1.10 → 1.12). Two runbook gaps folded back: (i) Phase 4a step 6 now includes a per-database assertion query that fails loud if any extension in wealthpay is below `default_version` (the bare `\dx` previously listed could show "1.12" while wealthpay was still at 1.10 if the operator queried the wrong DB); (ii) Phase 5 step 1 now greps the exporter container logs for SQL errors and checks the per-target `lastError` field in `/api/v1/targets`, since `up=1` is necessary but not sufficient — extending the WAL/checkpointer-presence checks the phase already had with this third detector. The bootstrap [`docker/postgres/bootstrap/03-pg-stat-statements.sql`](../docker/postgres/bootstrap/03-pg-stat-statements.sql) was hardened to self-heal version drift on fresh clones, but `pg_upgrade --link` reuses `PGDATA` so the bootstrap path is bypassed on cutover — the runbook step in Phase 4a step 6 remains the only safeguard for that path.
- **Watch-items deferred.** (i) The Phase 2.9 deferred check that `pg_stat_io WHERE object='wal' AND backend_type='walwriter'` produces the four `pg_wal_stat_*` series via sql-exporter — natural place is [Phase 5](#phase-5--bring-up-new-stack--verify-metric-pipeline) step 1's `curl localhost:9399/metrics | grep pg_wal_stat`. (ii) `delete_old_cluster.sh` deletion is owed at the end of Phase 7 once the new cluster has soaked.
- **Next phase:** [Phase 4.5](#phase-45--post-upgrade-data-preservation-check-before-any-new-write) — strict diff of preserved row counts against the Phase 0 baseline. Phase 4.5 is the gate before Phase 5; do not skip.

**Assigned agent.** `general-purpose` for the prep steps (1–3); **human runs steps 5 and 6** (the irreversible part). The agent prepares the volume layout, runs the upgrade container, and verifies the on-disk result, then *stops* and waits for human review of the `pg_upgrade` exit status before any Postgres process touches the new cluster. **Step 4 is an explicit "STOP" marker, not an action** — it exists to make the boundary visible inline, since agents read the step list, not just the header. Past step 5 (cluster up) hard rollback via `dumpall.before.sql` is the only recovery — agent autonomy ends at "ready to flip the switch", before any `vacuumdb`/`up -d` runs.

**Goal.** Move the cluster's data files in place from PG16 → PG18 layout via hard links, preserving planner statistics.

**Scope.** ~30 minutes including volume-layout setup.

**Pre-requisite.** The named volume `pg_data` is mounted at `/var/lib/postgresql/data` on the OLD compose layout (current state — see `docker-compose.local.yml:13`). **The volume's root contents *are* the data directory itself** — `PG_VERSION`, `pg_wal/`, `base/`, etc. live at the volume root. PG18 expects the cluster at `/var/lib/postgresql/18/docker`. The Phase 2.2a edit moves the compose mount from `/var/lib/postgresql/data` to `/var/lib/postgresql`, which means the new mount sees the volume root as the parent. We therefore need to relocate the existing cluster files from the volume root into a `16/docker/` subpath inside the same volume.

**Re-verify the volume name** before any volume operation in this phase — Compose may prefix named volumes (e.g. `wealthpay_pg_data`) unless `name:` is pinned in the compose file. Phase 2.9 step 0 already established this; re-run the check here as a safety net (cheap; failure mode of operating on the wrong volume is catastrophic):
```bash
docker volume inspect pg_data >/dev/null 2>&1 \
  || { echo "ABORT: volume 'pg_data' not found — see Phase 2.9 step 0 for resolution." >&2; exit 1; }
```
If the inspect fails, **stop**; substitute the real volume name throughout the rest of this phase.

**Steps.**

1. **One-time volume-layout migration.** With the stack stopped, move the cluster files from the volume root into the multi-version layout. The volume root *is* the cluster directory (not a parent of one), so the move is "stage everything at the root, then commit it under `16/docker/`":
   ```bash
   docker run --rm -v pg_data:/v alpine sh -c '
     set -eu
     cd /v
     # Pre-check: refuse to run if the new layout already exists or the
     # cluster signature is missing — both indicate the rename has already
     # happened or this is the wrong volume.
     if [ -e "/v/16" ] || [ -e "/v/18" ]; then
       echo "ABORT: /v/16 or /v/18 already exists — refusing to re-run." >&2
       exit 2
     fi
     if [ ! -f "/v/PG_VERSION" ]; then
       echo "ABORT: /v/PG_VERSION not found — volume root is not a PG cluster." >&2
       exit 3
     fi
     mkdir -p .stage-16
     find . -mindepth 1 -maxdepth 1 ! -name .stage-16 -exec mv {} .stage-16/ \;
     mkdir -p 16
     mv .stage-16 16/docker
     # Wrapper dirs come out of the mkdir/mv as root:root mode 0755; postgres
     # requires the data dir owned by postgres (UID 999) with mode 0700, or
     # it refuses to start. Surfaced by the Phase 2.9 rehearsal.
     chown -R 999:999 16
     chmod 0700 16/docker
   '
   ```
   This is a sequence of metadata-only `mv` operations within a single filesystem — no data copy, all renames are atomic per call. After it completes, the volume root contains exactly one entry, `/v/16/docker/` (the PG16 cluster) owned by `postgres:postgres` mode 0700, and PG18 will create `/v/18/docker/` once it starts. **Rollback for this step:** reverse the moves with `cd /v && mkdir -p .restore && find 16/docker -mindepth 1 -maxdepth 1 -exec mv {} .restore/ \; && rmdir 16/docker 16 && find .restore -mindepth 1 -maxdepth 1 -exec mv {} /v/ \; && rmdir .restore`.

2. **Run pg_upgrade in a transient container** with both binaries installed. **Default to Option B (hand-rolled).** Option A (`pgautoupgrade`) is purpose-built for this scenario but its CLI surface is opinionated — `--link` is governed by environment variables and image-baked entrypoint behaviour, not a passthrough flag — and the plan should not load-bearingly assume those defaults without verifying the image's README. Option B has explicit `pg_upgrade` flags so what runs is what the plan reads.

   Option A — `pgautoupgrade` (only if you have verified the image's documented behaviour for the version you pull):
   ```bash
   # Verify the image's CLI surface FIRST before running this:
   docker run --rm pgautoupgrade/pgautoupgrade:18-trixie --help 2>&1 | head -40
   # If the help output does not document a --link mode (or the equivalent
   # env var), abandon Option A and use Option B. Do NOT pass --link as a
   # flag without confirming the entrypoint forwards it.
   docker run --rm \
     -v pg_data:/var/lib/postgresql \
     -e PGAUTO_ONESHOT=yes \
     pgautoupgrade/pgautoupgrade:18-trixie
   ```
   The image detects the existing PG16 cluster, runs `pg_upgrade` (in `--link` mode by default per the project's docs at the time of writing), and exits.

   Option B — hand-rolled (preferred default). Pass the data directories as **literal paths** in the command-line arguments — earlier drafts of this plan set `-e PGDATA_OLD=...` and then referred to `$PGDATA_OLD` in the args, which is broken: `$PGDATA_OLD` is expanded by the *host* shell before `docker run` is invoked, while the `-e` only takes effect inside the container, so the args become empty strings unless the host already exports those vars. Literals avoid the expansion entirely.

   **Initialize the PG18 target datadir first — with locale/encoding/checksum settings extracted from the source.** `pg_upgrade` requires an existing, initialized new cluster *with matching encoding, locale, and checksum settings*; it will not create one, and any divergence makes `pg_upgrade --check` fail. Option A's entrypoint runs `initdb` for you; Option B does not. The Phase 2.9 rehearsal already proved this exact sequence against a copy of this volume — re-use the locale value you captured there.

   First, confirm what the source cluster used (re-extracting at this point catches drift in the unlikely case the source was reinitialized between Phase 2.9 and now):
   ```bash
   docker run --rm \
     -v pg_data:/var/lib/postgresql:ro \
     ghcr.io/<org>/pg-upgrader:18 \
       /usr/lib/postgresql/16/bin/pg_controldata /var/lib/postgresql/16/docker | \
     grep -E 'LC_COLLATE|LC_CTYPE|Database block size|Data page checksum'
   ```
   STOP if the values differ from what Phase 2.9's rehearsal used; the rehearsal no longer represents the upgrade you're about to run, and Phase 2.9 must be re-rehearsed.

   Then `initdb` the target — substitute the captured locale. Note the two non-default flags below — they are load-bearing on this cluster (Phase 2.9 surfaced both):
   - `--no-data-checksums` — PG18's `initdb` enables data page checksums **by default**, but our source cluster has `Data page checksum version: 0`. They must match or `pg_upgrade --check` fails. (If your source ever has `Data page checksum version: 1`, drop this flag and pass `--data-checksums` instead — but verify the source first.)
   - `--username=user` — this cluster's superuser is `user` (POSTGRES_USER from compose), not the `postgres` default. Without this flag, `pg_upgrade --check` later fails on the new-cluster side with `role "user" does not exist`.
   ```bash
   docker run --rm \
     -v pg_data:/var/lib/postgresql \
     ghcr.io/<org>/pg-upgrader:18 \
       bash -lc '
         set -euo pipefail
         rm -rf /var/lib/postgresql/18/docker
         mkdir -p /var/lib/postgresql/18/docker
         chown -R postgres:postgres /var/lib/postgresql/18
         chmod 0700 /var/lib/postgresql/18/docker
         # Substitute the locale below with the value from pg_controldata above.
         su postgres -c "/usr/lib/postgresql/18/bin/initdb \
           -D /var/lib/postgresql/18/docker \
           --encoding=UTF8 \
           --locale=en_US.utf8 \
           --no-data-checksums \
           --username=user"
       '
   ```
   **Run `pg_upgrade --check` first** (~30s), even though Phase 2.9 already passed it on a copy of this volume. Real-world drift between rehearsal and execution is cheap to catch here and catastrophic to discover after the `--link` cliff is crossed.

   Two pg_upgrade flag groups below are load-bearing (Phase 2.9 surfaced both — same reasons as the `initdb` invocation above plus a third):
   - `--username=user` — superuser name must match the source.
   - `--old-options="..."` and `--new-options="..."` — `pg_upgrade` starts both clusters internally and does **not** re-read compose's `command:` `-c` flags. With `pg_cron` and `pg_stat_statements` only set via compose `command:` (not persisted in `postgresql.conf`/`postgresql.auto.conf`), the source startup falls back to `wal_level=replica` and `pg_cron` is not loadable on either side. Passing the preload through `-o`/`-O` makes both internal startups match production. The source-side startup also requires the `debezium` logical slot to be already gone — Phase 3 step 6 handles that.

   Two `docker run` flags below are also load-bearing (Phase 4a real-execution surfaced both):
   - `--user postgres` — `pg_upgrade` refuses to run as `root` (it shells out to `postgres` server processes internally and the parent must already be `postgres`).
   - `--workdir <writable path>` — `pg_upgrade` writes its log files (`pg_upgrade_internal.log`, `pg_upgrade_server.log`, `pg_upgrade_output.d/`) into the current working directory; the upgrader image's default `WORKDIR` is not writable by `postgres` and the bare `docker run` fails with `You must have read and write access in the current directory. Failure, exiting`. Use `/tmp` for `--check` (logs are throwaway) and `/var/lib/postgresql/18/docker` for `--link` so `pg_upgrade_output.d/` lands inside the new datadir where step 6's `update_extensions.sql` extraction expects it.

   ```bash
   docker run --rm \
     -v pg_data:/var/lib/postgresql \
     --user postgres \
     --workdir /tmp \
     ghcr.io/<org>/pg-upgrader:18 \
       /usr/lib/postgresql/18/bin/pg_upgrade --check \
         --old-bindir=/usr/lib/postgresql/16/bin \
         --new-bindir=/usr/lib/postgresql/18/bin \
         --old-datadir=/var/lib/postgresql/16/docker \
         --new-datadir=/var/lib/postgresql/18/docker \
         --username=user \
         --old-options="-c shared_preload_libraries=pg_cron,pg_stat_statements -c cron.database_name=wealthpay" \
         --new-options="-c shared_preload_libraries=pg_cron,pg_stat_statements -c cron.database_name=wealthpay"
   ```
   STOP if `--check` exits non-zero — `--link` will fail in the same way and the cliff is uncrossed at this point. Investigate the diff between this volume and the Phase 2.9 rehearsal copy.

   Then run the upgrade itself with the same flag set (only `--check` removed; `--workdir` switched so `pg_upgrade_output.d/` lands inside the new datadir):
   ```bash
   docker run --rm \
     -v pg_data:/var/lib/postgresql \
     --user postgres \
     --workdir /var/lib/postgresql/18/docker \
     ghcr.io/<org>/pg-upgrader:18 \
       /usr/lib/postgresql/18/bin/pg_upgrade --link \
         --old-bindir=/usr/lib/postgresql/16/bin \
         --new-bindir=/usr/lib/postgresql/18/bin \
         --old-datadir=/var/lib/postgresql/16/docker \
         --new-datadir=/var/lib/postgresql/18/docker \
         --username=user \
         --old-options="-c shared_preload_libraries=pg_cron,pg_stat_statements -c cron.database_name=wealthpay" \
         --new-options="-c shared_preload_libraries=pg_cron,pg_stat_statements -c cron.database_name=wealthpay"
   ```
   If you prefer to keep the directory paths in env vars for readability, expand them inside the container via `sh -c` — same flag set, just the datadirs hoisted:
   ```bash
   docker run --rm \
     -v pg_data:/var/lib/postgresql \
     --user postgres \
     --workdir /var/lib/postgresql/18/docker \
     -e PGDATA_OLD=/var/lib/postgresql/16/docker \
     -e PGDATA_NEW=/var/lib/postgresql/18/docker \
     ghcr.io/<org>/pg-upgrader:18 \
       sh -c '/usr/lib/postgresql/18/bin/pg_upgrade --link \
         --old-bindir=/usr/lib/postgresql/16/bin \
         --new-bindir=/usr/lib/postgresql/18/bin \
         --old-datadir="$PGDATA_OLD" \
         --new-datadir="$PGDATA_NEW" \
         --username=user \
         --old-options="-c shared_preload_libraries=pg_cron,pg_stat_statements -c cron.database_name=wealthpay" \
         --new-options="-c shared_preload_libraries=pg_cron,pg_stat_statements -c cron.database_name=wealthpay"'
   ```
   Either form is correct; the literal form is the documented default. Building this image is left as a sub-task; the in-tree reference is [`docker/pg-upgrader/Dockerfile`](../docker/pg-upgrader/Dockerfile) (committed in Phase 2.9 as `wealthpay-pg-upgrader:18` — substitute that tag for `ghcr.io/<org>/pg-upgrader:18` if you have not pushed it to a registry).

3. **Verify exit status is 0** and the new cluster directory exists:
   ```bash
   docker run --rm -v pg_data:/v alpine ls -la /v/18/docker /v/16/docker
   ```
   Both should be present at this point. The PG16 directory is now a write-locked husk — its files have been hard-linked into PG18 and any write to either side corrupts both.

4. **STOP — agent boundary, hand off to human.** At this point the volume contains both `/v/16/docker` and `/v/18/docker` with shared inodes via `--link`, **but no Postgres process has yet started against the new layout** — running the rollback snippet from step 1 (which moves the contents of `16/docker/` back to the volume root and removes the `16` and `18` subdirs) is still a clean recovery. The next step starts Postgres against `/v/18/docker`; once it accepts a single write, the hard-link cliff is crossed and only `dumpall.before.sql` can recover. Agent: report the step 3 output and stop.

5. **Bring the stack up (HUMAN-OWNED — this is the irreversibility cliff).** Compose's volume mount is now at `/var/lib/postgresql` per the Phase 2 edit; PG18 picks up `/var/lib/postgresql/18/docker` via its baked-in `PGDATA`.

   **Pre-cliff prep — two operations to run before `up -d`** (Phase 4a real-execution surfaced both as required):

   *(a) Rebuild the runtime image.* The locally-cached `wealthpay-postgres:latest` may predate the Phase 2 Dockerfile bump (still PG16). Without this rebuild, the cached PG16 binary attempts to read the PG18-upgraded datadir and refuses to start with a catalog-version mismatch:
   ```bash
   ./scripts/infra.sh build postgres
   docker images wealthpay-postgres   # verify CREATED is post-`a1438ff` and digest changed
   ```

   *(b) Copy `pg_hba.conf` from the old datadir to the new one.* `pg_upgrade` does not copy `pg_hba.conf` (its docs are explicit). The new cluster ships with the default initdb file, which only allows localhost connections. The PG16 cluster had `host all all all scram-sha-256` appended by docker-entrypoint.sh during PG16's first boot via `POSTGRES_HOST_AUTH_METHOD`; without that line, sibling containers in the Docker network (Spring app, kafka-connect, postgres-exporter, sql-exporter) get rejected by PG18 with `FATAL: no pg_hba.conf entry for host …`. Phase 2.9's rehearsal missed this because its psql probes used localhost. Pre-cliff is the safest place to fix it (`pg_hba.conf` is config, not data — copying it does not interfere with the hard-linked relation files):
   ```bash
   docker run --rm -v pg_data:/v alpine sh -c '
     cp /v/16/docker/pg_hba.conf /v/18/docker/pg_hba.conf
     chown 999:999 /v/18/docker/pg_hba.conf
     chmod 0600 /v/18/docker/pg_hba.conf
   '
   ```

   **Then bring the stack up:**
   ```bash
   ./scripts/infra.sh up -d
   ```

6. **Run the post-upgrade analyze and the `update_extensions.sql` housekeeping (HUMAN-OWNED).** PG18 preserves planner statistics through `pg_upgrade` from PG14+ sources, but the docs recommend a final pass to fill any gaps. Run **after** the cluster is up and accepting connections from step 5:
   ```bash
   docker compose exec -T postgres vacuumdb -U user --all --analyze-in-stages --missing-stats-only
   ```
   This step *requires* the cluster to be running — that's why it lives on the human side of the boundary. An earlier draft of this plan placed the `vacuumdb` before `up -d` via a transient container; that was wrong because it would have started Postgres against the new layout before the human had reviewed the upgrade exit status.

   Then apply the extension-version bump that `pg_upgrade` emitted (Phase 2.9 surfaced the contents — `pg_stat_statements` 1.10 → 1.12; `pg_cron` 1.6 → 1.6 = no change). The script is left in the new datadir alongside `pg_upgrade`'s logs.

   **Extensions are per-database.** `pg_stat_statements` is registered separately in each database it is created in, so `ALTER EXTENSION ... UPDATE` must run **inside the database whose view postgres-exporter scrapes** (here: `wealthpay`, set by `DATA_SOURCE_URI` in [docker-compose.local.yml](../docker-compose.local.yml)). A `\dx` listing in the wrong DB can show "1.12" while `wealthpay` is still at 1.10 — and postgres-exporter v0.19.1+ then errors on every scrape with `column pg_stat_statements.shared_blk_read_time does not exist` (the column was renamed in pgss 1.11), with `up{job="wealthpay-db"}=1` throughout because per-query SQL errors do not fail the HTTP scrape. The post-execution finding on 2026-05-02 was exactly this. The bootstrap [`docker/postgres/bootstrap/03-pg-stat-statements.sql`](../docker/postgres/bootstrap/03-pg-stat-statements.sql) self-heals this on fresh clones, but `pg_upgrade --link` reuses an existing `PGDATA` so the bootstrap is **bypassed** on the cutover path — this step is the only line of defense.
   ```bash
   # 1) Apply the bump in the wealthpay DB explicitly (-d wealthpay is load-bearing).
   docker compose exec -T postgres psql -U user -d wealthpay -c "ALTER EXTENSION pg_stat_statements UPDATE;"

   # 2) ASSERT per-database: every installed extension in wealthpay must be at default_version.
   #    Returns 0 rows on success; any output is a drift you must investigate before proceeding.
   docker compose exec -T postgres psql -U user -d wealthpay -v ON_ERROR_STOP=1 -c "
     SELECT e.extname, e.extversion AS installed, ae.default_version AS expected
       FROM pg_extension e
       JOIN pg_available_extensions ae ON ae.name = e.extname
      WHERE e.extversion <> ae.default_version;
   "

   # 3) Visual confirmation (snapshot of post-update versions for the runbook log).
   docker compose exec -T postgres psql -U user -d wealthpay -c "SELECT extname, extversion FROM pg_extension ORDER BY extname;"
   ```
   Expected post-update: `pg_cron 1.6`, `pg_stat_statements 1.12`, `plpgsql 1.0`, and the assertion query (#2) returns **zero rows**. If #2 returns any row, **stop** and re-run `ALTER EXTENSION <extname> UPDATE` in the wealthpay DB until the assertion is empty. If the script left in the datadir lists more `ALTER EXTENSION ... UPDATE` lines than these (e.g. an extension was added between the rehearsal and execution), apply each one — re-extract the script from `/var/lib/postgresql/18/docker/pg_upgrade_output.d/<timestamp>/update_extensions.sql` (or wherever the upgrade log directory landed) before running. Sanity-check the `-d` flag: the per-DB nature of the failure mode means a successful-looking step here is not enough; the assertion query in #2 is what closes the gap.

### Acceptance criterion

- `pg_upgrade` exits 0.
- Post-upgrade `psql -c "SELECT version();"` reports PG18 after the human-owned start in step 5.
- R1 (data preservation) is **not** asserted here — it is owned by [Phase 4.5](#phase-45--post-upgrade-data-preservation-check-before-any-new-write), which runs after `vacuumdb` and before any new write. Phase 5 must not proceed until Phase 4.5 has signed off Flyway state and preserved-table row counts. (Earlier drafts asserted "All row counts from Phase 0.5 match" here, but that duplicated the Phase 4.5 contract and used a baseline filename that no longer exists after the row-counts split.)

### On failure

- Any non-zero exit from `pg_upgrade` itself: **stop, preserve evidence, do not start either cluster.** Capture the full pg_upgrade log and the contents of `/v/16/docker` and `/v/18/docker` (`alpine ls -la`). The hard-link cliff is a spectrum — `pg_upgrade --link` may have linked some files and not others before failing. Whether the *old* cluster can be safely restarted depends on how far the upgrade got: if hard-link creation completed for system catalogs, restarting PG16 against `/v/16/docker` may corrupt rows shared with the partially-linked PG18 directory. **Default recovery is the dumpall path** ([Hard rollback](#hard-rollback-any-state-including-post--link-corruption)) — it has zero ambiguity and bounded data loss. Restoring by reversing the volume-layout move and starting PG16 against `/v/16/docker` is *only* acceptable if a human reads the pg_upgrade log, confirms the upgrade aborted before any catalog hard-link was made, and accepts the residual risk. Rehearsing this path in [Phase 2.9](#phase-29--build-and-rehearse-the-pg-upgrader-image-blocking-gate-before-phase-3) is the way to develop the judgment for that decision; it should not be made for the first time during a real outage.
- Any error after step 5 starts the new cluster: the old cluster is now corrupted. **Rollback path is via `dumpall.before.sql`** — see [Rollback procedures](#rollback-procedures).

---

## Phase 4b — Execute `pg_dumpall + restore` (DOCUMENTED FALLBACK ONLY — not the chosen method)

**Status.** **DO NOT EXECUTE** without first reopening [Phase 1](#phase-1--method-decision) and getting human sign-off. This phase is retained for completeness; the chosen method is `--link` ([Phase 4a](#phase-4a--execute-pg_upgrade---link-chosen-method)).

**Assigned agent (if reopened).** `general-purpose`. Lower stakes than 4a (rollback stays trivial); a single agent can execute end-to-end.

**Goal.** Bring up an empty PG18 cluster, restore from the safety-net dump. Old PG16 volume is left untouched as the rollback substrate.

**Scope.** ~15 minutes.

**Steps.**

1. **Rename the old volume aside** so PG18 starts fresh:
   ```bash
   docker volume create pg_data_pg16_backup
   docker run --rm -v pg_data:/from -v pg_data_pg16_backup:/to alpine \
     sh -c 'cp -a /from/. /to/'
   docker volume rm pg_data
   docker volume create pg_data
   ```
   `cp -a` rather than `mv` keeps the source in place for an extra belt-and-braces rollback.

2. **Bring up the PG18 cluster only:**
   ```bash
   docker compose -f docker-compose.local.yml up -d postgres postgres-bootstrap
   docker compose logs -f postgres
   # Wait for "database system is ready to accept connections"
   ```
   At this point the cluster is empty except for the bootstrap-applied extensions and monitoring role. Flyway has not run yet — the application is still down.

3. **Restore the dump:**
   ```bash
   docker compose exec -T postgres psql -U user -d postgres \
     < ~/wealthpay-pg-upgrade-baseline/dumpall.before.sql
   ```
   The `--clean --if-exists` flags from Phase 3.4 mean the script first drops everything; this is harmless on an empty cluster.

4. **Run ANALYZE manually** (dumpall does not transfer pg_stats):
   ```bash
   docker compose exec -T postgres vacuumdb -U user -d wealthpay --analyze-only
   ```

5. **Bring up the rest of the stack:**
   ```bash
   ./scripts/infra.sh up -d
   ```

### Acceptance criterion

- `psql -c "SELECT version();"` reports PG18.
- Row counts from Phase 0.5 match.
- `flyway_schema_history` has all migrations marked `success=t` (carried over by the dump).
- The original `pg_data_pg16_backup` volume still exists and is untouched (`docker volume inspect pg_data_pg16_backup`).

### On failure

- Restore failed mid-stream: the PG18 volume is partial. Drop it, recreate, retry the restore from the same dump.
- Confirmed-broken state: see [Rollback procedures](#rollback-procedures).

---

## Phase 4.5 — Post-upgrade data preservation check (BEFORE any new write)

**Status (2026-05-02): EXECUTED — R1 (data preservation) PASSED.** All four acceptance criteria met. PG version reports `PostgreSQL 18.3 (Debian 18.3-1.pgdg13+1) on aarch64-unknown-linux-gnu`. `flyway_schema_history` strict diff **empty** (18 rows, V1–V17, every `success=t`). Preserved-table strict diff **empty** — `event_store=2 124 327`, `account_balance_view=718 004`, `account_snapshot=35`, `processed_transactions=1 159 354`, `processed_reservations=248 088`, `outbox_cleanup_log=19` all match Phase 0 baseline exactly. Outbox informational diff also empty (`41 681` = baseline). pg_cron history: 19 successful runs, all pre-upgrade (latest `2026-05-01 03:00`, cluster start `2026-05-01 19:58`); next firing of the daily 3 AM `manage_outbox_partitions()` job is `2026-05-02 03:00`, ~1 h after this check completed. Cron-history rowcount of 19 exactly matches the `outbox_cleanup_log` rowcount of 19 — invariant intact (the function writes one row to `outbox_cleanup_log` per execution). Spring application not running (`ps -ef` clean of `[Ww]ealthpay`). The pre-existing collation-version warning is no longer emitted on the trixie-based PG18 image, confirming the Phase 0 prediction. Full execution log at `~/wealthpay-pg-upgrade-baseline/phase-4.5-execution.md`.

- **Real-execution findings.**
  1. **Step 4 SQL is wrong as written** — `cron.job_run_details` does not have `schedule` or `last_run_status` columns; `schedule` lives on `cron.job` (the *definition* table) and the run-details outcome column is named `status` (not `last_run_status`). The query as printed in the runbook fails with `ERROR: column "schedule" does not exist`. Corrected query joins the two tables via `LEFT JOIN cron.job j USING (jobid)` and selects `j.jobname, j.schedule, jr.command, jr.status, jr.start_time, jr.end_time`. Folded into step 4 below.
  2. **Phase 3 step 5 deferred connector DELETE caused active CDC carryover on the new cluster.** Phase 3 step 5 (`DELETE /connectors/wealthpay-outbox-connector`) was deferred because kafka-connect was in `Created` (never-started) state at Phase 3 kickoff, so the REST API was unreachable. The connector *config* remained in the Kafka `connect-configs` topic. When Phase 4a step 5 brought up the new stack, Kafka Connect rehydrated the connector and started it against PG18; Debezium auto-created the `debezium` logical slot on the new cluster (default `slot.drop.on.stop=false`, slot is created on first start if missing). At Phase 4.5 check time (cluster up 6h), connector reports `RUNNING` (worker `kafka-connect:8083`, task 0 RUNNING, version `3.1.2.Final`); slot is active (`active_pid=96`, `restart_lsn=2/E7041938`, `confirmed_flush_lsn=2/E7041970`, retention 1054 kB).
     - **Why R1 is unaffected:** `table.include.list=account.outbox` (only the outbox table is captured), and the outbox count is unchanged at 41 681 = baseline — so zero CDC events have been streamed on the new cluster. Debezium is a CDC *reader*; it never writes back to source tables. The `connect-offsets` topic carries forward the pre-upgrade "snapshot completed" marker, so the connector did NOT perform an initial snapshot on the brand-new slot — it ran in effective `snapshot.mode=never` semantics by accident.
     - **Why Phase 6 inherits a non-clean state:** Phase 6 step 0 assumes the connector is absent. With the legacy connector RUNNING and the legacy `debezium` slot ACTIVE, Phase 6 step 0 must explicitly DELETE the legacy connector AND drop its slot **before** creating `debezium_pg18` (Path C default). Otherwise the new connector + the old connector would both stream WAL into `wealthpay.AccountEvent` (same `topic.prefix` and EventRouter `topic.replacement` — neither depends on connector name), producing duplicate Kafka traffic and violating Phase 6's "no duplicate Kafka traffic" decision. Folded into Phase 6 step 0 as an explicit prep step BEFORE the offset inspection.
  3. **Pre-existing observation cleared.** The Phase 0 collation-version warning (catalog 2.41 vs OS 2.36) is **no longer emitted** on the trixie-based PG18 image — the Phase 2.1 image flip from bookworm to trixie aligned runtime glibc with the catalog stamp, exactly as predicted in Phase 0. Phase 5 does not need to handle it as a stderr-noise carve-out.
- **Cluster state at end of Phase 4.5.** PG18.3 up; data preserved; R1 contract proven. Spring Boot **not running** (no listener on `:8080`, no `mvn spring-boot:run` process). Kafka Connect up (healthy); legacy `wealthpay-outbox-connector` RUNNING; legacy `debezium` slot ACTIVE on PG18 — to be cleaned up at Phase 6 step 0.
- **Next phase:** [Phase 5](#phase-5--bring-up-new-stack--verify-metric-pipeline) — exporter health (3-check), metric inventory diff, WAL/checkpointer probe, Prometheus scrape verification, Grafana visual. Stack is *already up* (since Phase 4a step 5), so Phase 5's "stack up" pre-requisite is already met.

**Assigned agent.** `general-purpose`. Read-only psql + diff; no cluster mutation.

**Goal.** Prove R1 (data preservation) **before** anything downstream introduces new rows. After Phase 6 starts the application and Phase 7 runs Gatling, raw row counts will legitimately diverge from the Phase 0 baseline — that's the writer doing its job, not a bug. The strict R1 contract therefore has to be checked *here*, in the window between `vacuumdb` (Phase 4a step 6) and the first new write (Phase 6 step 7).

**Scope.** ~5 minutes.

**Pre-requisite.** Phase 4a step 6 (`vacuumdb`) complete. Debezium connector is **not yet recreated** (Phase 6). Application is **not running**. No `INSERT`, `UPDATE`, or `DELETE` has been issued against the new cluster since it started.

**Steps.**

1. Confirm PostgreSQL version reports PG18:
   ```bash
   docker compose exec -T postgres psql -U user -d wealthpay -c "SELECT version();"
   ```
2. Confirm Flyway schema-history survived the upgrade:
   ```bash
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT installed_rank, version, description, success
        FROM flyway_schema_history
       ORDER BY installed_rank;" \
     > ~/wealthpay-pg-upgrade-baseline/flyway.after
   diff ~/wealthpay-pg-upgrade-baseline/flyway.before ~/wealthpay-pg-upgrade-baseline/flyway.after
   ```
   Diff must be empty. Any difference means the migration history was lost or corrupted by the upgrade.
3. Re-run the row-count queries from Phase 0 step 5 — **two separate captures**, matching the split there. The strict diff is over the preserved file (must be empty); the outbox count is informational and may legitimately differ due to 3-day retention:
   ```bash
   # Preserved tables — STRICT diff (must be empty for Phase 4.5 to pass).
   docker compose exec -T postgres psql -U user -d wealthpay -F$'\t' -c \
     "SELECT 'event_store' AS t, count(*) FROM account.event_store
      UNION ALL SELECT 'account_balance_view',     count(*) FROM account.account_balance_view
      UNION ALL SELECT 'account_snapshot',         count(*) FROM account.account_snapshot
      UNION ALL SELECT 'processed_transactions',   count(*) FROM account.processed_transactions
      UNION ALL SELECT 'processed_reservations',   count(*) FROM account.processed_reservations
      UNION ALL SELECT 'outbox_cleanup_log',       count(*) FROM account.outbox_cleanup_log;" \
     > ~/wealthpay-pg-upgrade-baseline/row-counts.preserved.after.initial
   diff ~/wealthpay-pg-upgrade-baseline/row-counts.preserved.before \
        ~/wealthpay-pg-upgrade-baseline/row-counts.preserved.after.initial
   # Outbox — INFORMATIONAL only (the partition retention windowing legitimately drifts).
   docker compose exec -T postgres psql -U user -d wealthpay -F$'\t' -c \
     "SELECT 'outbox (all partitions)' AS t, count(*) FROM account.outbox;" \
     > ~/wealthpay-pg-upgrade-baseline/row-counts.outbox.after.initial
   # Show the outbox delta for the PR description, but do NOT fail on it.
   diff ~/wealthpay-pg-upgrade-baseline/row-counts.outbox.before \
        ~/wealthpay-pg-upgrade-baseline/row-counts.outbox.after.initial || true
   ```
4. Confirm no application writes have happened. The application is not running and (modulo the Phase 3 step 5 carryover — see below) the connector is not registered, so the only possible writers are autovacuum/checkpointer (neither writes user-table rows) and any pg_cron jobs scheduled to fire on cluster start. **Note for executing agents:** an earlier draft of this query referenced `schedule` and `last_run_status` columns directly on `cron.job_run_details`; both columns are wrong. `schedule` lives on `cron.job` (the *definition* table), and the outcome column on the run-details table is named `status`. Use the joined query below instead — folded back from the 2026-05-02 execution:
   ```bash
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT jr.jobid, j.jobname, j.schedule, jr.command, jr.status, jr.start_time, jr.end_time
        FROM cron.job_run_details jr
        LEFT JOIN cron.job j USING (jobid)
       ORDER BY jr.end_time DESC NULLS LAST LIMIT 20;"
   ```
   Plus an aggregate sanity check (the cron-runs count should match the corresponding count of rows the cron job is known to write — for `outbox-partition-cleanup`, that's `outbox_cleanup_log`):
   ```bash
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT count(*) AS total_runs,
             count(*) FILTER (WHERE status='succeeded') AS ok,
             count(*) FILTER (WHERE status='failed')    AS failed,
             min(start_time) AS earliest,
             max(end_time)   AS latest
        FROM cron.job_run_details;"
   ```
   If a pg_cron job ran since the cluster came up and that job mutates a table in the contract list (e.g. `outbox_cleanup_log`), capture and explain the count delta in the PR description; otherwise treat any count difference as a regression. **If the latest `end_time` predates `pg_postmaster_start_time()`, no cron writes have happened on the new cluster** — that's the cleanest possible signal.

   **Also confirm the Phase 3 step 5 carryover state.** If Phase 3 step 5's `DELETE /connectors/wealthpay-outbox-connector` was deferred (because kafka-connect was not running at Phase 3 time), Kafka Connect will have rehydrated the legacy connector from `connect-configs` on the new stack and Debezium will have auto-created a fresh `debezium` logical slot. Verify whether this happened — it does not invalidate R1 (Debezium is a CDC reader; `table.include.list=account.outbox`, whose count is in the informational diff above), but Phase 6 step 0 owes the cleanup before registering the new connector:
   ```bash
   curl -s http://localhost:8083/connectors           # expect: [] (clean) OR ["wealthpay-outbox-connector"] (carryover)
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT slot_name, slot_type, active, active_pid, restart_lsn FROM pg_replication_slots;"
   ```
   If both queries are non-empty, document the state in the PR description and ensure Phase 6 step 0's cleanup runs before its offset inspection.

**Acceptance criteria.**
- PostgreSQL reports version 18.
- `flyway_schema_history` diff is empty.
- The `row-counts.preserved.before` vs `row-counts.preserved.after.initial` diff is **empty** — all six preserved tables match exactly. (The outbox diff is informational and is not part of acceptance — it is captured for the PR description but allowed to differ.)
- No application writes have occurred (verified by step 4).

**On failure.**
- Any preserved-table row count differs and step 4 does not explain it: the upgrade lost data. **Stop**, escalate to the human owner. Do not proceed to Phase 5. Hard rollback via `dumpall.before.sql` is the recovery path.
- `flyway_schema_history` rows missing: the upgrade did not preserve the schema-history table. Same recovery path.

---

## Phase 5 — Bring up new stack & verify metric pipeline

**Status (2026-05-02): EXECUTED — R2 (metrics behaviour) substantially proven; full closure deferred to [Phase 7](#phase-7--final-verification-cdc-consistency--metrics-behaviour) Gatling run.** All eight steps PASSED after one fix landed in [`docker/sql-exporter/wal-io.collector.yml`](../docker/sql-exporter/wal-io.collector.yml). Step 1's 3-check protocol caught the regression on first run — exporters were `up=1` and Prometheus `lastError=""` throughout, but the **container log scan** surfaced continuous `"was collected before with the same name and label values"` errors on the four `pg_wal_stat_{write,sync}_{total,_time_milliseconds_total}` series (the very series the Phase 2.9 deferred check (i) was supposed to verify). Root cause: on PG18 GA, `SELECT … FROM pg_stat_io WHERE object='wal' AND backend_type='walwriter'` returns **two rows** (one per `context` value: `init`, `normal`), but the four metrics are defined labels-free (single-row contract). Two-row × labels-free → duplicate emission → Prometheus rejection. Fix: rewrite `pg_stat_io_wal` to `SUM(writes/fsyncs/write_time/fsync_time) FROM pg_stat_io WHERE object='wal'` (no `backend_type` filter). This is the semantically correct reconstruction of the legacy cluster-wide `pg_stat_wal.{wal_write,wal_sync,…}` totals — the original `walwriter`-only filter understated cluster-wide WAL writes by ~36× on this stack (walwriter is one of seven backend types issuing WAL writes; `standalone backend` from Flyway dominates at 759 of 819 writes). After restart: 8 `pg_wal_stat_*` series clean, `pg_wal_stat_write_total` corrected from 23 → 819 (cluster-wide). SQL-only probe (50 000-row INSERT + explicit `CHECKPOINT`) advanced WAL records 44 127 → 146 045 (+101 918), bytes 8.7 MB → 19.6 MB (+10.9 MB), `pg_stat_checkpointer_num_requested_total` 7 → 8 (the explicit CHECKPOINT), `buffers_full_total` 0 → 827 (the wal_buffers backpressure signal is wired correctly). Metric inventory diff bounded to two clean categories: (a) the canonical PG17 `pg_stat_bgwriter` → `pg_stat_checkpointer` migration (7 removed / 9 added — runbook predicted 4/4; postgres-exporter v0.19.1 emits the full view including standby-only restartpoints + `stats_reset_total`), and (b) PG18 GUC drift on `pg_settings_*` (deprecated `db_user_namespace` / `log_connections` / `old_snapshot_threshold_seconds` removed; ~28 new tunables surfaced including `io_workers`, `vacuum_truncate`, `transaction_timeout_seconds`). The runbook's predicted `pg_replication_slots_inactive_since_seconds` is correctly ABSENT — the `debezium` slot is `active=t, inactive_since=NULL`, and the collector's own contract is "ABSENT while slot is active". `pg_stat_statements_block_read_seconds_total` series emitting cleanly, confirming the Phase 4a step 6 extension bump landed in the `wealthpay` DB. Three runbook gaps folded back:
- **Finding 1 — Phase 2.4 / [postgres-upgrade.md §PG18](postgres-upgrade.md#pg18--pg_stat_wal-columns-relocate-to-pg_stat_io) fix recipe was wrong on PG18 GA.** The `WHERE backend_type='walwriter'` filter (a) returns two rows on the actual PG18.3 catalog due to the `(init, normal)` context split, and (b) understates cluster-wide WAL writes. Fix recipe rewritten in-place to use `SUM(...)` across all `object='wal'` rows. Codebase change committed; runbook updated.
- **Finding 2 — Phase 5 step 4 expected line count is `≥ 4`, not `== 4`.** postgres-exporter v0.19.1 exposes the full `pg_stat_checkpointer` view (9 series including 3 standby-only restartpoint counters and `stats_reset_total`). The four critical renames (`num_timed`, `num_requested`, `write_time`, `sync_time`) are all present; the surplus is pure addition. Step 4's `wc -l == 4` assertion relaxed to "≥ 4 with all four core renames present".
- **Finding 3 — Phase 5 step 6 `up{job=~".*postgres.*"}` filter is too narrow.** It matches only `postgres-exporter`, missing `sql-exporter`. Broaden to `up{job=~".*(postgres|sql).*"}` or just `up`. Verified all PG-feeding targets `up=1` (and the `wealthpay` app target `up=0` as required by the Phase 6 step 0 pre-condition).

Step 8 (Grafana visual) verified by proxy — all three named dashboards (`Database — Server Health`, `Database — Query & Table Performance`, `Account Service — Application SLIs`) reachable; all panel-feeding PromQL queries return values. Final visual walk-through is the residual human check.

- **Cluster state at end of Phase 5.** PG18.3 up; metric pipeline clean; Spring Boot **not running** (Phase 6 step 0 pre-condition met); legacy `wealthpay-outbox-connector` + `debezium` slot still in carryover state from Phase 4.5 — to be cleaned up at Phase 6 step 0 per the existing plan. Working tree on `feat/postgres-migration` with one new commit (the `wal-io.collector.yml` SUM fix).
- **Next phase:** [Phase 6 — Recreate Debezium connector](#phase-6--recreate-debezium-connector). Full execution log at `~/wealthpay-pg-upgrade-baseline/phase-5-execution.md`.

**Assigned agent.** `general-purpose`. curl + diff + Grafana visual check; no code edits.

**Goal.** Confirm every metric series that existed before is being emitted now (R2). Catch silent breakages introduced by the upgrade.

**Scope.** ~15 minutes.

**Pre-requisite.** Phase 4a or 4b complete **and** [Phase 4.5](#phase-45--post-upgrade-data-preservation-check-before-any-new-write) passed (R1 proved). Stack up with the Phase 2 branch checked out. If Phase 4.5 was skipped, STOP and run it first — Phase 5's metric checks are valid only on a cluster whose data survived the upgrade.

**Steps.**

1. Confirm both exporters are healthy. **Three checks, not one** — the Phase 4a (2026-05-02) post-execution finding showed that `curl -sf` alone is insufficient: a per-query SQL error inside postgres-exporter v0.19.1 (e.g. `column pg_stat_statements.shared_blk_read_time does not exist` if Phase 4a step 6's extension bump was missed in the wealthpay DB) is logged at ERROR level but the scrape **still returns HTTP 200** with the surviving metrics, so `up{job="wealthpay-db"}` stays at `1` and `InstanceDown` does not fire. All three checks must pass:
   ```bash
   # (a) HTTP plane: exporter is up and serving /metrics.
   curl -sf localhost:9187/metrics > /dev/null && echo "postgres-exporter HTTP OK"
   curl -sf localhost:9399/metrics > /dev/null && echo "sql-exporter HTTP OK"

   # (b) Container logs: no per-query SQL errors in the last 5 minutes.
   #     Empty output = pass; any line is a wired-but-failing collector that (a) cannot detect.
   docker logs --since 5m wealthpay-postgres-exporter 2>&1 | grep -iE 'error|level=err' || echo "postgres-exporter logs clean"
   docker logs --since 5m wealthpay-sql-exporter      2>&1 | grep -iE 'error|level=err' || echo "sql-exporter logs clean"

   # (c) Prometheus' view of every PG-related target: lastError must be empty.
   #     This is the cross-check against (a) — Prometheus records the per-scrape error
   #     even when `up=1` (errors during metric parsing, not connection refused).
   curl -sG 'http://localhost:9090/api/v1/targets' \
     | jq -r '.data.activeTargets[]
              | select(.labels.job | test("postgres|sql.exporter|wealthpay"))
              | "\(.labels.job)\t\(.health)\tlastError=\(.lastError)"'
   ```
   Acceptance for (b) and (c): zero matching log lines and `lastError=""` for every PG-related target. **Any non-empty `lastError` or any error log line is a fail** — fix before proceeding to step 2; the metric-name diff in step 2 cannot detect a failing per-query collector if the failure leaves no surviving series at all in the post-baseline. Likeliest root cause if `pg_stat_statements_*` is the failing collector: the wealthpay DB's `pg_stat_statements` extension is still at 1.10 — return to [Phase 4a step 6](#phase-4a--execute-pg_upgrade---link-chosen-method) and run the per-database assertion query.
2. **Capture the post-upgrade metric inventory and diff:**
   ```bash
   # Same label-stripping sed as Phase 0 — keep both captures parser-identical so the diff is meaningful.
   curl -s localhost:9187/metrics | sed -nE 's/^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?[[:space:]].*/\1/p' | grep -E '^pg_' | sort -u > ~/wealthpay-pg-upgrade-baseline/postgres-exporter.after
   curl -s localhost:9399/metrics | sed -nE 's/^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?[[:space:]].*/\1/p' | grep -E '^pg_' | sort -u > ~/wealthpay-pg-upgrade-baseline/sql-exporter.after
   diff ~/wealthpay-pg-upgrade-baseline/postgres-exporter.before ~/wealthpay-pg-upgrade-baseline/postgres-exporter.after
   diff ~/wealthpay-pg-upgrade-baseline/sql-exporter.before     ~/wealthpay-pg-upgrade-baseline/sql-exporter.after
   ```
   The expected diff is **bounded and known**:
   - `postgres-exporter`: `-` four `pg_stat_bgwriter_checkpoint*` series; `+` four `pg_stat_checkpointer_*` series. Plus possibly some new `pg_stat_io_*` byte counters introduced by PG18 (`read_bytes`, `write_bytes`, `extend_bytes`).
   - `sql-exporter`: ideally **no diff at all** for the eight `pg_wal_stat_*` series (the Phase 2.4 rewiring preserves names). One new metric `pg_replication_slots_inactive_since_seconds` should appear.
   - Any other diff is a regression and Phase 5 is not done.

3. **Verify the eight WAL metrics are present** (presence is the contract here — advancement is enforced after the SQL probe in step 5):
   ```bash
   curl -s localhost:9399/metrics | grep -E '^pg_wal_stat_' | wc -l   # expect: 8
   curl -s localhost:9399/metrics | grep -E '^pg_wal_stat_'           # capture for the diff in step 5
   ```

4. **Verify the checkpointer metrics are present.** The runbook originally said "expect: 4" — that was wrong; postgres-exporter v0.19.1 exposes the full `pg_stat_checkpointer` view, which on PG18 emits **9 series** (the four core renames + `buffers_written_total` + 3 standby-only `restartpoints_*` counters, all 0 on a primary, + `stats_reset_total`). Acceptance is presence of the four core renames (and ≥ 4 series total):
   ```bash
   curl -s localhost:9187/metrics | grep -E '^pg_stat_checkpointer_' | wc -l   # expect: ≥ 4 (typically 9 on a primary)
   curl -s localhost:9187/metrics | grep -E '^pg_stat_checkpointer_'           # capture for the diff in step 5
   # Hard-required: the four core renames must be in the list.
   curl -s localhost:9187/metrics \
     | grep -E '^pg_stat_checkpointer_(num_timed|num_requested|write_time|sync_time)_total\b' \
     | wc -l   # expect: 4 (acceptance gate)
   ```

5. **Trigger a SQL-only WAL/checkpoint probe and prove the counters *advance*.** Phase 5's pre-condition is "application not yet running, connector not yet registered" — running Gatling here would require starting the application, which would then violate the [Phase 6 step 0](#phase-6--recreate-debezium-connector) contract for `snapshot.mode=never` (events written before the slot exists are silently dropped). The probe is database-local so it does not need the application *or* Debezium:
   ```bash
   docker compose exec -T postgres psql -U user -d wealthpay -c "
     CREATE SCHEMA IF NOT EXISTS upgrade_probe;
     CREATE TABLE IF NOT EXISTS upgrade_probe.wal_probe(
       id      bigserial PRIMARY KEY,
       payload text
     );
     INSERT INTO upgrade_probe.wal_probe(payload)
     SELECT repeat(md5(g::text), 100)
       FROM generate_series(1, 50000) AS g;
     CHECKPOINT;
   "
   ```
   ~10 seconds. Bulk insert drives WAL records/bytes; explicit `CHECKPOINT` forces at least one checkpointer counter to advance. After it completes, re-capture the same `grep` outputs from steps 3 and 4 and apply this layered acceptance rule — uniform "every counter strictly greater" is too brittle (some timing counters legitimately stay at zero unless `track_wal_io_timing` is on and the workload produces measurable timing):
   - **WAL records OR WAL bytes must advance.** At least one of `pg_wal_stat_records_total` / `pg_wal_stat_bytes_total` shows a strictly-greater value than its pre-probe capture.
   - **At least one checkpointer counter must advance** (`pg_stat_checkpointer_num_requested_total` is the most likely, since the explicit `CHECKPOINT` is a requested checkpoint).
   - **Timing counters must be present** (eight `pg_wal_stat_*_time_*` lines, two `pg_stat_checkpointer_*_time_total` lines).
   - **Timing counters MAY remain zero** if the underlying GUCs (`track_wal_io_timing`, `track_io_timing`) are off or if the probe was too small to register a non-zero timing — log a warning, do not fail the phase.

   Any counter that fails the *first two* rules (records/bytes or at least one checkpointer counter) points at a wiring problem in the relevant collector — see [postgres-upgrade.md](postgres-upgrade.md) for the fix recipes.

   **Cleanup** (do not leave the probe schema behind):
   ```bash
   docker compose exec -T postgres psql -U user -d wealthpay -c "DROP SCHEMA upgrade_probe CASCADE;"
   ```

6. **Confirm Prometheus is scraping cleanly.** The original regex `up{job=~".*postgres.*"}` was too narrow — it matches `postgres-exporter` only, missing `sql-exporter`. Use the broader vector and inspect every PG-feeding target:
   ```bash
   curl -sG 'http://localhost:9090/api/v1/query' --data-urlencode 'query=up' \
     | jq -r '.data.result[] | "\(.metric.job)\t\(.metric.instance)\tvalue=\(.value[1])"'
   ```
   All PG-feeding targets (`postgres-exporter`, `sql-exporter`) must show `value=1`. The `wealthpay` (Spring Boot app) target **must** show `value=0` — Phase 5's pre-condition is that the application is not running, and `up=1` for `wealthpay` here would indicate a Phase 6 step 0 pre-condition violation, not a Phase 5 success.

7. **Confirm no alert is silently NoData:**
   ```bash
   curl -sG 'http://localhost:9090/api/v1/rules' | jq '.data.groups[].rules[] | select(.health != "ok")'
   ```
   Empty output.

8. **Visually verify dashboards:** open Grafana (`http://localhost:3000`, `admin`/`password`) and walk through:
   - `Database — Server Health` — panels #8–#11 (WAL) and #12–#13 (checkpoint) all show data.
   - `Database — Query & Table Performance` — `pg_stat_statements` panels render. (Note: the table is empty post-upgrade by design — pg_stat_statements lives only in shared memory. The panels will look "right but empty" until query traffic warms them up. The SQL probe from step 5 may populate a small number of entries — full warm-up of application-query panels happens later, after Phase 7's Gatling spike runs against the live application.)
   - `Account Service` — application-level dashboard renders. (Spring Boot side is unaffected by the PG version, but a JDBC-driver mismatch surfaces here first.)

### Acceptance criterion

- The four diff items in step 2 are exactly the expected set; no others.
- Steps 3 and 4 each emit the expected number of lines (8 and 4 respectively); presence required.
- After the SQL probe in step 5, the layered acceptance rule passes: at least one of WAL records / WAL bytes advances; at least one checkpointer counter advances; timing counters are present (advancement of timing counters is best-effort, not required).
- All `up` values are `1`. No `health: nodata` rules.
- Dashboards render without "No data" panels.

### On failure

- A `pg_wal_stat_*` series missing: Phase 2.4 fix recipe was not applied correctly. Check `wal-io.collector.yml` against the recipe in [postgres-upgrade.md §PG18](postgres-upgrade.md#pg18--pg_stat_wal-columns-relocate-to-pg_stat_io).
- A `pg_stat_checkpointer_*` series missing: postgres-exporter does not have `--collector.stat_checkpointer` in its argv. Check `docker compose ps postgres-exporter` and the compose `command:` block.
- A WAL records/bytes counter is **present** but does **not advance** after the SQL probe: the collector is wired but the underlying view is returning a stuck/static value. Inspect the `wal-io.collector.yml` mapping against the live `pg_stat_io` shape on the new cluster. For `pg_wal_stat_write_time_milliseconds_total` and `pg_wal_stat_sync_time_milliseconds_total` specifically, non-advancement is almost always `track_wal_io_timing=off` on the new cluster — verify the compose `command:` block carried that GUC across the upgrade. (Per the layered rule, timing-counter non-advancement is a warning, not a failure.) For checkpointer counters: the probe issues an explicit `CHECKPOINT;`, so `pg_stat_checkpointer_num_requested_total` should advance — if it doesn't, the postgres-exporter `--collector.stat_checkpointer` flag is missing or the exporter isn't scraping the right port.
- **Hard rollback territory.** Phase 5 runs *after* Phase 4a step 5 (cluster started, hard-link cliff crossed). If the issue cannot be patched by editing exporter config and bouncing the exporter container, recovery is the [hard-rollback procedure](#hard-rollback-any-state-including-post--link-corruption) via `dumpall.before.sql` — do not "live with" a missing metric series, since downstream alerts will be silently NoData.

---

## Phase 6 — Recreate Debezium connector

**Status (2026-05-02): EXECUTED — Path C, `snapshot.mode=never`, end-to-end smoke test PASSED.** Step 0 prep cleanup did real work (the predicted carryover state was present): legacy `wealthpay-outbox-connector` was live and the `debezium` slot was held with `active_pid=96`; DELETE returned HTTP 204, slot released within 5 s (`active_pid=NULL`), `pg_drop_replication_slot('debezium')` succeeded; both re-inspections clean. Step 0 offset inspection: 12 stored offsets found on this stack, top `lsn=12067445008` (≈ `2/CF464ED0`, matches the Phase 0–era flushed LSN); current PG18 WAL was `2/E7C4F8B8` — well ahead of the stored top, so Path A would also have been safe but Path C avoids the question entirely. `dbz_publication` survived `--link` cleanly (covers `account.outbox`, INSERT/UPDATE/DELETE/TRUNCATE). Debezium PostgresConnector version `3.1.2.Final` accepts both `never` (deprecated alias) and `no_data` (canonical 3.x spelling); kept `never` per runbook default. Edits to [`debezium/register-connector.sh`](../debezium/register-connector.sh): connector renamed to `wealthpay-outbox-connector-pg18` in both DELETE URL and `name` field, `slot.name=debezium_pg18` added, `snapshot.mode=never` added — re-registration POST returned HTTP 201, status check shows `connector.state=RUNNING` and `tasks[0].state=RUNNING` within 8 s. Slot `debezium_pg18` came up `active=t, slot_type=logical, confirmed_flush_lsn=2/E7C52640, retention_bytes=7424` (7.4 KB). App started in 3.161 s after sourcing `.env`; smoke test (`POST /accounts` with the real flat-schema payload, see Step 7 correction below) returned HTTP 201 with `accountId=9e55ce31-2e8d-4762-b0fc-ba3e00e0dce1`; `account.account_balance_view` rowcount 718 004 → 718 005 within 3 s; balance `100.0000` USD projected — full WAL → Debezium → Kafka → AccountOutboxConsumer → projection chain healthy. Three runbook drifts folded inline below: (i) Step 0 offset-inspection command used the wrong kafka service name (`kafka` vs the actual `kafka-1`–`kafka-3`), wrong listener (`localhost:9092` from inside a kafka container hits its HOST listener at the wrong protocol — must use the INTERNAL listener `kafka-1:29092`), and wrong topic name (`connect-offsets` vs the configured `connect_offsets` per `CONNECT_OFFSET_STORAGE_TOPIC` in `docker-compose.local.yml`); (ii) Step 6 `mvn spring-boot:run` from a fresh shell fails with `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${DB_URL}` because `.env` is loaded by docker-compose and IDE run configs but not by a bare `mvn` — preamble added; (iii) Step 7 smoke-test snippet was stale (`/api/v1/accounts` and a nested `openingBalance` payload with `ownerId`); the real OpenAPI contract is `POST /accounts` with the flat schema `{accountCurrency, initialAmount, initialAmountCurrency}` and no `ownerId` in the request body — the response carries the server-generated `accountId`. Stack remains up; application is running. **Next phase:** [Phase 7](#phase-7--final-verification-cdc-consistency--metrics-behaviour) (R2 metrics under load + R3 no-silent-CDC-data-loss via Gatling spike).

**Assigned agent.** `general-purpose`. curl + one JSON edit to [`debezium/register-connector.sh`](../debezium/register-connector.sh).

**Goal.** Restart CDC against the new cluster's freshly-created logical slot, with no duplicate snapshot. **`snapshot.mode=never` is the chosen contract** (see TL;DR) — Phase 6.3 below has the implementation details.

**Scope.** ~10 minutes.

**Pre-requisite.** Phase 5 passed. Application has **not** been started against the new cluster. (Earlier wording allowed "started but idle"; that's an agent-unsafe assumption — "idle" is a human-eyeballed state, and `snapshot.mode=never` will silently drop any event written between cluster-up and slot-creation regardless of whether the writer was "intending" to be idle. Strict: the application is `Down`, no `mvn spring-boot:run` process exists.)

**Steps.**

> **Naming variables for this phase.** Set these once at the top of your terminal session and reuse them in every step below — keeps step 4's status check, the slot query in step 5, and Appendix A's probe in lockstep with the Path C decision. The defaults below assume Path C is chosen (the documented default):
> ```bash
> CONNECTOR_NAME=wealthpay-outbox-connector-pg18   # new identity (Path C default); set to "wealthpay-outbox-connector" for Path A or B
> SLOT_NAME=debezium_pg18                          # new slot identity; set to "debezium" for Path A or B (the Debezium default)
> ```
> Debezium's PostgreSQL connector default for `slot.name` is literally `"debezium"` — **not** derived from the connector name. So renaming the connector alone does NOT isolate the slot; you have to set `slot.name` explicitly in the connector config (step 2 below). If both names aren't moved, a partial rollback could leave the new connector pointing at the old slot, defeating the isolation that Path C exists for.

> **Step 0 prep — clean up any Phase 3 step 5 carryover BEFORE the offset inspection.** Added 2026-05-02 after Phase 4.5 found that the legacy connector + a recreated `debezium` slot were both live on the new PG18 cluster (Phase 3 step 5's DELETE was deferred because kafka-connect was in `Created` state at Phase 3 kickoff; Kafka Connect rehydrated the connector config from `connect-configs` once the new stack came up). Phase 4.5's R1 contract is unaffected by this state (Debezium is a CDC reader, `table.include.list=account.outbox` has zero new rows on the new cluster), but the legacy connector + slot must be removed before the new connector + slot are created — otherwise both connectors would stream WAL into the same `wealthpay.AccountEvent` topic (`topic.prefix=wealthpay` + the EventRouter `topic.replacement` — neither depends on connector name), producing duplicate Kafka traffic that violates the `snapshot.mode=never` "no duplicate Kafka traffic" decision.
> ```bash
> # Inspect first — this is a no-op in the clean case and a real cleanup in the carryover case.
> curl -s http://localhost:8083/connectors                                                                    # expect: [] (clean)
> docker compose exec -T postgres psql -U user -d wealthpay -c \
>   "SELECT slot_name, active, active_pid FROM pg_replication_slots WHERE slot_name='debezium';"             # expect: zero rows (clean)
>
> # If either inspection reports state, clean up (DELETE first, then wait, then drop the slot — order matters):
> curl -X DELETE -s -o /dev/null -w "HTTP %{http_code}\n" \
>   http://localhost:8083/connectors/wealthpay-outbox-connector
> sleep 5    # let kafka-connect release the slot — pg_drop_replication_slot fails if active_pid is non-null
> docker compose exec -T postgres psql -U user -d wealthpay -c \
>   "SELECT pg_drop_replication_slot('debezium');"
> ```
> Re-run the two inspect commands and confirm both are empty (no connectors, no `debezium` slot) before proceeding to the offset inspection below.

0. **Inspect Kafka Connect's stored offsets for the connector and decide explicitly how to handle them.** Connector deletion in Phase 3 dropped the connector's *runtime state* but not necessarily its *offset state* in Kafka Connect's internal offsets topic (configured as `connect_offsets` on this stack — see `CONNECT_OFFSET_STORAGE_TOPIC` in `docker-compose.local.yml`; the upstream Kafka Connect default is `connect-offsets` with a hyphen, but **this stack uses an underscore**, so the canonical-default name is a silent false negative against this cluster). With `snapshot.mode=never`, Debezium's behaviour on a re-registration depends on whether it finds a previously-stored LSN for the same connector name. Three paths exist; the plan must pick one:

   ```bash
   # Inspect: list any messages in the connect_offsets topic keyed for this connector.
   #
   # Three corrections vs. the upstream defaults that bite on this stack:
   #   1. Service name is kafka-1 / kafka-2 / kafka-3 (3-node KRaft cluster) — there is no `kafka` service.
   #   2. From inside the kafka-N container, use the INTERNAL listener (kafka-1:29092). The HOST
   #      listener at localhost:9092 is for the host machine; hitting it from inside the container
   #      yields `UnsupportedVersionException: The node does not support METADATA` (the in-container
   #      bootstrap reaches the wrong listener plane).
   #   3. Topic name is connect_offsets (underscore), not connect-offsets (hyphen) — see above.
   docker compose exec -T kafka-1 kafka-console-consumer \
     --bootstrap-server kafka-1:29092 \
     --topic connect_offsets \
     --from-beginning --timeout-ms 5000 \
     --property print.key=true --property print.value=true \
     2>/dev/null | grep -F 'wealthpay-outbox-connector' || echo "no offsets stored"
   ```

   - **Path C — use a new connector name (DEFAULT).** Register the post-upgrade connector as `wealthpay-outbox-connector-pg18` (or similar). Kafka Connect treats it as a new connector with no stored offsets, so `snapshot.mode=never` starts at `pg_current_wal_lsn()` of the new cluster — exactly the contract Phase 6 needs. **Verified safe for this stack:** topic names are derived from `topic.prefix: "wealthpay"` and the EventRouter SMT's `topic.replacement: "wealthpay.${routedByValue}"`, neither of which depends on the connector name. The downstream `AccountOutboxConsumer` reads from `wealthpay.AccountEvent` and is unaffected by the rename. The only follow-up is deleting the old connector's metadata — schedule this as a separate ops ticket; do not delete it during the cutover. Also rename `database.server.name` (Debezium's logical server identity) if your config sets it, for the same isolation reason.
   - **Path A — reuse offsets (do nothing).** Acceptable if the stored LSN is from before Phase 3 stopped the cluster *and* the new cluster's WAL position is at or beyond it (true after `--link` since LSN space carries through). Debezium will resume from the stored LSN. **Risk:** if the stored LSN is greater than `pg_current_wal_lsn()` on the new cluster (possible if Phase 3 took the dump *before* the slot fully drained, contradicting the plan's contract), the connector silently drops everything between. Verify by comparing the stored LSN against `pg_current_wal_lsn()` before path A is chosen.
   - **Path B — clear offsets explicitly.** Delete the connector's entries from `connect-offsets`, or use Kafka Connect's `DELETE /connectors/<name>/offsets` endpoint (available in Connect 3.6+; requires the connector to be `STOPPED`, not just `PAUSED`). **Caveat:** the exact API differs by Connect version, and manually mutating the compacted `connect-offsets` topic is fragile. Only choose Path B if you can pin the exact, version-supported reset command in the PR description before the cutover. Do not improvise.

   **Default:** Path C. Document the chosen path and the chosen connector name in the PR description. If Path A is chosen, the LSN comparison must be in the PR description as evidence. If Path B is chosen, the exact `DELETE` command (with Connect version captured) must be in the PR description before execution.

1. **Recreate the publication if Phase 4b was used.** (Phase 4a's `--link` preserves it, dumpall does not always; verify either way):
   ```bash
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT pubname FROM pg_publication;"
   # Expect: dbz_publication
   ```
   If absent, re-run Flyway migration V15/V16 manually or trigger Spring Boot to run it (it's idempotent; safe).

2. **Verify the installed Debezium version accepts `snapshot.mode=never`, then edit [`debezium/register-connector.sh`](../debezium/register-connector.sh).** Recent Debezium versions added `no_data` as the new spelling for the same semantic; older versions only accept `never`. Confirm one of them works against the running Kafka Connect:
   ```bash
   curl -s http://localhost:8083/connector-plugins | jq -r '.[] | select(.class | contains("PostgresConnector")) | .version'
   # Cross-reference the version against the Debezium release notes for snapshot.mode=never vs no_data.
   ```
   In the PR description, capture one of the two outcomes:
   - "Debezium version `<X.Y.Z>` accepts `snapshot.mode=never`; `no_data` migration deferred."
   - "Debezium version `<X.Y.Z>` requires `snapshot.mode=no_data`; using that spelling instead."
   The rest of this phase assumes `never`; substitute `no_data` throughout if the second outcome applies.

   **Then edit the script — two changes.** First, add the `snapshot.mode` line (the locked-in production-strict contract from the TL;DR):
   ```diff
        "table.include.list": "account.outbox",
        "plugin.name": "pgoutput",
   +    "snapshot.mode": "never",
        "key.converter": "org.apache.kafka.connect.storage.StringConverter",
   ```
   Second — **if you chose Path C in step 0 (the default)** — rename the connector AND set `slot.name` explicitly so Kafka Connect treats this as a brand-new connector AND so the underlying replication slot is also a fresh identity (without an explicit `slot.name`, Debezium uses the literal default `"debezium"` regardless of the connector name — so renaming the connector alone leaves the slot name unchanged):
   ```diff
   -    "name": "wealthpay-outbox-connector",
   +    "name": "wealthpay-outbox-connector-pg18",
        "config": {
          ...
   +      "slot.name": "debezium_pg18",
          ...
        }
   ```
   The two `curl` URLs at the top of the script also need the new connector name. **Topic naming is unaffected** — `topic.prefix` and the EventRouter `topic.replacement` field remain `"wealthpay"` / `"wealthpay.${routedByValue}"`, so downstream consumers reading `wealthpay.AccountEvent` see no change. Steps 4 and 5 below use `${CONNECTOR_NAME}` and `${SLOT_NAME}` from the variable block at the top of this phase — they automatically pick up the new identities.

   Why `snapshot.mode=never` is the chosen contract: the application's downstream consumer is idempotent (processed-transaction tracking would absorb duplicates), but the production-strict contract is desired here for learning-project realism. The trade-off is that the slot's first read after recreation must be at or after the LSN of any event we care about delivering — see step 3's pre-condition check.

3. **Re-register the connector** with the now-strict config:
   ```bash
   ./debezium/register-connector.sh
   ```
   This script issues a `DELETE` first (safe — it 404s if absent), then `POST`s the updated connector config. **Critical pre-condition:** at this point the application is **still down** (started in step 6), so no events are being written between the slot recreation and the connector start. The `snapshot.mode=never` contract holds because the LSN window is empty. If you reverse the order — start the app first, then register the connector — events written during that window are silently dropped by the connector, which is exactly the failure mode `snapshot.mode=never` exists to enforce against.

4. **Confirm the connector is RUNNING** within ~30s. Uses `${CONNECTOR_NAME}` from the variable block — under Path C this is `wealthpay-outbox-connector-pg18`, NOT the old `wealthpay-outbox-connector` (a 404 here usually means you forgot to set the variable):
   ```bash
   curl -sf "http://localhost:8083/connectors/${CONNECTOR_NAME}/status" \
     | jq '.connector.state, .tasks[].state'
   # Expected: "RUNNING" "RUNNING"
   ```

5. **Confirm the slot is back, active, and has appropriate WAL retention.** Filter by `${SLOT_NAME}` — selecting all rows from `pg_replication_slots` would mask the very thing we're checking (e.g. a stale `debezium` slot still hanging around alongside the new `debezium_pg18`):
   ```bash
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT slot_name, slot_type, active, confirmed_flush_lsn,
             pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS retention_bytes
        FROM pg_replication_slots
       WHERE slot_name = '${SLOT_NAME}';"
   ```
   `active=t`, `retention_bytes` low (single-digit MB or less). If the query returns zero rows, the slot was never created — the connector likely failed to start and step 4's status check should have caught that.

6. **Start the application.** `.env` is loaded by docker-compose and the IDE run configs, but **not** by a bare `mvn spring-boot:run`. Without the env vars in scope, Flyway's HikariCP DataSource sees the literal placeholder `${DB_URL}` and aborts with `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${DB_URL}` during bean initialization. Source `.env` into the shell first (the `set -a / set +a` pair auto-exports the assignments and then restores normal behavior):
   ```bash
   set -a && source .env && set +a
   mvn spring-boot:run
   ```

7. **Smoke test — open one account, check it shows up downstream.** The real OpenAPI contract (see [`src/main/resources/openapi/account/account-api.yaml`](../src/main/resources/openapi/account/account-api.yaml)) is `POST /accounts` (no `/api/v1` prefix) with the **flat** schema `{accountCurrency, initialAmount, initialAmountCurrency}` — there is no `ownerId` in the request body, and no nested `openingBalance` wrapper. The response carries the server-generated `accountId` (UUID), which is the correct way to find the row in `account_balance_view` (`ORDER BY account_id DESC LIMIT 1` is wrong here — the column is a UUID, not a monotonic id, and won't sort to "newest"). The rowcount delta `+1` is also a robust end-to-end check that doesn't depend on shell-parsing the JSON response:
   ```bash
   BEFORE=$(docker compose -f docker-compose.local.yml exec -T postgres psql -U user -d wealthpay -At -c \
     "SELECT count(*) FROM account.account_balance_view;")
   ACCOUNT_ID=$(curl -sS -X POST http://localhost:8080/accounts \
     -H 'Content-Type: application/json' \
     -d '{"accountCurrency":"USD","initialAmount":100.00,"initialAmountCurrency":"USD"}' \
     | jq -r '.accountId')
   sleep 3
   AFTER=$(docker compose -f docker-compose.local.yml exec -T postgres psql -U user -d wealthpay -At -c \
     "SELECT count(*) FROM account.account_balance_view;")
   echo "before=$BEFORE  after=$AFTER  account_id=$ACCOUNT_ID"
   docker compose -f docker-compose.local.yml exec -T postgres psql -U user -d wealthpay -c \
     "SELECT account_id, balance FROM account.account_balance_view WHERE account_id = '$ACCOUNT_ID';"
   ```
   `AFTER` must be `BEFORE + 1`, and the lookup row must show the deposited amount (`100.0000` for the `100.00` USD opening). This exercises the entire REST → DB (event_store + outbox in one transaction) → WAL → Debezium → Kafka topic `wealthpay.AccountEvent` → `AccountOutboxConsumer` → projection chain.

### Acceptance criterion

- Connector is RUNNING and slot is active.
- The smoke-test account flows end-to-end from REST → DB → Debezium → Kafka → projection within 5 seconds.

### On failure

- Connector FAILED with "replication slot does not exist": Phase 4 did not preserve the slot (expected for `--link` from PG16 source) and the connector's first call to `CREATE_REPLICATION_SLOT` was rejected. Check that `wal_level=logical` is set on the new cluster (compose `command:` block) and that `pgoutput` is the configured `plugin.name`. Re-create with `DELETE` then `POST` again.
- Connector RUNNING but no events flowing: `dbz_publication` may be missing or tabled wrong (V15/V16 set `publish_via_partition_root=true`; without it, table.include.list mismatches partition names). Re-run V16 manually.
- **Smoke-test event missing from `account_balance_view` AND no connector errors** — the LSN-window contract for `snapshot.mode=never` was violated: events were written between cluster-up and slot-creation and were silently dropped (this is exactly the failure mode the strict mode exists to *expose*, not to prevent). **Escape hatch:** edit `debezium/register-connector.sh` to set `"snapshot.mode": "initial"` for one cycle, `DELETE` + `POST` again, and accept the brief duplicate burst — downstream idempotency via `processed_transactions` / `processed_reservations` absorbs duplicates, which is the contract those tables exist for. Once the slot has caught up and a fresh smoke test passes, revert to `"snapshot.mode": "never"` and re-register a final time. Document the escape-hatch use in the PR description; this is acceptable as a one-time recovery, not as a steady state.

---

## Phase 7 — Final verification: CDC consistency & metrics behaviour

**Assigned agent.** `general-purpose` for execution + `code-reviewer` for an independent read of the diffs and the PR before merge.

**Goal.** Prove R2 (metrics behaviour) and R3 (no silent CDC data loss) in writing. R1 (data preservation) is **owned by [Phase 4.5](#phase-45--post-upgrade-data-preservation-check-before-any-new-write)** and was already proved before any application write occurred — re-running the strict row-count diff here would fail-by-design because Phase 6's smoke test and Phase 7's own Gatling spike legitimately add rows. Phase 7 instead proves the *consistency* properties that survive new writes: every aggregate is projected, the slot stays healthy under load, and the metrics pipeline keeps emitting.

**Scope.** ~15 minutes for the execution agent; ~30 minutes for the reviewer.

**Steps.**

1. **R2 — metrics behaviour:** the diff from Phase 5.2 has already been validated. Take a final snapshot for the PR description:
   ```bash
   diff ~/wealthpay-pg-upgrade-baseline/postgres-exporter.before ~/wealthpay-pg-upgrade-baseline/postgres-exporter.after
   diff ~/wealthpay-pg-upgrade-baseline/sql-exporter.before     ~/wealthpay-pg-upgrade-baseline/sql-exporter.after
   ```

2. **R3 — no silent CDC data loss:** the smoke test from Phase 6.7 covers one happy-path event. To exercise the failure-mode coverage, run a Gatling spike and confirm the projection catches up. The two tables grow on different axes — `event_store` is **per event** (open + N credits/debits + close per account), `account_balance_view` is **per aggregate** (one row per opened account, updated in place by subsequent events) — so they do not grow proportionally. Use two narrower invariants instead of comparing raw counts. **Capture `pg_postmaster_start_time` first** so invariant 2 can be cutover-anchored (the question the migration must answer is "did the *new* cluster's CDC drop anything?", not "is there any drift across all of history?" — pre-cutover drift carried over by `pg_upgrade --link` is preserved data, not a migration-induced regression):
   ```bash
   mvn -Pgatling gatling:test
   sleep 30

   # Capture cutover boundary (pg_postmaster_start_time of the PG18 cluster)
   CUTOVER_TS=$(docker compose exec -T postgres psql -U user -d wealthpay -At -c \
     "SELECT pg_postmaster_start_time();")
   echo "cutover boundary: ${CUTOVER_TS}"

   # Invariant 1: both counts strictly increased vs Phase 0 baseline (proves CDC produced *some* output)
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT count(*) AS event_store_count FROM account.event_store;"
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT count(*) AS balance_view_count FROM account.account_balance_view;"

   # Invariant 2 (LOAD-BEARING, cutover-anchored): every aggregate whose
   # FIRST event landed AFTER the PG18 cluster started must be projected
   # into account_balance_view. This is the migration-relevant question.
   docker compose exec -T postgres psql -U user -d wealthpay -c "
     WITH post_cutover_aggregates AS (
       SELECT account_id
         FROM account.event_store
        GROUP BY account_id
       HAVING min(created_at) > '${CUTOVER_TS}'
     )
     SELECT count(*) AS post_cutover_aggregates_missing_from_projection
       FROM (SELECT account_id FROM post_cutover_aggregates
             EXCEPT
             SELECT account_id FROM account.account_balance_view) miss;
   "

   # Invariant 2 — strict form (forensics only): catches ALL drift, including
   # pre-cutover legacy state. Use to surface debt, not as a stop condition.
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT
        (SELECT count(DISTINCT account_id) FROM account.event_store)        AS distinct_aggregates_in_event_store,
        (SELECT count(*)                   FROM account.account_balance_view) AS rows_in_balance_view;"
   ```
   Pass conditions: (a) both counts in invariant 1 are strictly greater than the Phase 0 baseline; (b) `post_cutover_aggregates_missing_from_projection = 0` — every aggregate created after the new cluster started is projected. The strict form may be unequal when pre-cutover drift exists in the source data; in that case run the per-aggregate forensic queries (date-bucket the missing set, inspect event-type mix, check whether all misses fall in a bounded historical window) and document the root cause in the PR. **A non-zero result for the cutover-anchored form is a silent CDC drop and Phase 7 fails.**

3. **R3 — slot retention healthy.** Filter by `${SLOT_NAME}` (set in Phase 6 — under Path C this is `debezium_pg18`):
   ```bash
   : "${SLOT_NAME:=debezium_pg18}"   # default to Path C name; override if Path A/B was chosen
   docker compose exec -T postgres psql -U user -d wealthpay -c \
     "SELECT slot_name, active,
             pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn) AS lag_bytes,
             pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)         AS retention_bytes,
             inactive_since, invalidation_reason
        FROM pg_replication_slots
       WHERE slot_name = '${SLOT_NAME}';"
   ```
   Lag and retention both single-digit MB or less. Zero rows means the slot was never created — the connector likely failed.

4. **Alert state diff** — confirm no alert is silently NoData or unexpectedly firing:
   ```bash
   curl -sG http://localhost:9090/api/v1/alerts | jq '.data.alerts[] | {state, labels}' \
     > ~/wealthpay-pg-upgrade-baseline/alerts.after
   diff ~/wealthpay-pg-upgrade-baseline/alerts.before ~/wealthpay-pg-upgrade-baseline/alerts.after
   ```
   The diff should be empty or trivial. Any new firing alert must be investigated and either resolved or explicitly accepted (with a comment in the PR).

### Acceptance criterion

- R1 was already proved in [Phase 4.5](#phase-45--post-upgrade-data-preservation-check-before-any-new-write); Phase 7 inherits that result.
- R2 (step 1): the metric inventory diffs match Phase 5.2's bounded set. On postgres-exporter v0.19.x against PG18 this is dominated by (a) the bgwriter→checkpointer rename family — Phase 5.2 requires the four core renames present (`num_timed`, `num_requested`, `write_time`, `sync_time`); the exporter actually emits ≥9 `pg_stat_checkpointer_*` series, which is fine — and (b) PG18 `pg_settings_*` GUC churn (added/removed/renamed parameters between PG16 and PG18). Items predicted by earlier drafts that may legitimately be absent on this stack: `pg_stat_io_*_bytes_total` (postgres-exporter v0.19 doesn't emit them) and `pg_replication_slots_inactive_since_seconds` (only present when the slot is inactive — an active slot correctly emits no series). No deltas outside the checkpointer-rename + GUC-churn families.
- R3 (step 2): both invariants pass — counts are strictly greater than baseline, and the cutover-anchored projection check (`post_cutover_aggregates_missing_from_projection`) returns 0. The strict-equality form is forensics-only; a strict mismatch is acceptable iff every missing aggregate's first event predates `pg_postmaster_start_time` of the new cluster (pre-cutover drift faithfully preserved by `pg_upgrade --link`). A non-zero post-cutover miss is a silent CDC drop and Phase 7 fails.
- R3 (step 3): replication-slot lag and retention are both single-digit MB or less.
- (Step 4): alert-state diff is empty or trivial; any new firing alert is investigated and explicitly accepted in the PR.
- The PR description gets a "Verification" section embedding each diff inline.

---

## Phase 8 — Re-baseline ADR-008 & merge

**Assigned agent.** `general-purpose` for the ADR/runbook edits; **human performs the merge** (it is the irreversible-on-`main` step). The agent prepares the diffs and waits for human sign-off; the agent does not run `git merge`, `gh pr merge`, or `git push`.

**Goal.** Update the long-lived calibration log in [ADR-008](adr/008-db-observability-slos.md) so future operators see the post-PG18 baseline, not the stale pre-PG18 one. Merge the Phase 2 PR.

**Scope.** ~30 minutes including the Gatling re-baseline.

**Steps.**

1. **Run the calibration workload** from ADR-008's "Calibration log" section:
   ```bash
   mvn -Pgatling gatling:test
   ```
   Same shape as the existing baseline run. Capture Gatling's pass/fail counts and p50/p95/p99/max for the PR.

2. **Sample the under-load metrics** at the workload's peak:
   ```bash
   # During the ramp-and-hold phase (60s window), in another terminal:
   curl -s localhost:9187/metrics | grep -E '^(pg_stat_database_blks_hit|pg_stat_database_blks_read)'
   curl -s localhost:9399/metrics | grep -E '^pg_replication_slots_wal_retention_bytes'
   curl -s localhost:9187/metrics | grep -E '^pg_stat_checkpointer_'
   ```

3. **Update [ADR-008](adr/008-db-observability-slos.md):**
   - The "SLI inventory" table — replace the under-load baseline column for any metric whose value materially changed (≥10%). Note the date.
   - The "Calibration log" table — add a new row dated 2026-04-30 with method "PG18 post-upgrade" and the same Gatling pass/p50/p95/p99/max format.
   - The "Why DbCacheHitRatioLow exists despite a 100% baseline" section — re-confirm the under-load baseline is still ≥0.95. If not, the ADR's own re-baseline trigger has fired and the alert moves from structural-safety-net to workload-derived. This is unlikely on this dataset but the check is mandatory.
   - The PG16 reference in "Why `inactive_age_seconds` is a recording rule, not a SQL metric" — append a note that the recording rule was removed in the PG18 upgrade and the alert now references `pg_replication_slots_inactive_since_seconds` directly. (Or rewrite the section as historical context.)

4. **Update [postgres-upgrade.md](postgres-upgrade.md):**
   - Change the opening line "Current stack version: **PostgreSQL 16**" to "Current stack version: **PostgreSQL 18**".
   - Mark the PG17 and PG18 hazard sections as "Applied — see [postgres-18-migration-plan.md](postgres-18-migration-plan.md) Phase 2.4 and 2.6."
   - The document still has long-term value: PG19 will introduce its own hazards. Keep the structure.

5. **Merge the Phase 2 PR.**

### Acceptance criterion

- ADR-008's "SLI inventory" reflects post-PG18 numbers, dated.
- ADR-008's "Calibration log" has a new row.
- postgres-upgrade.md's "Current stack version" header is updated.
- PR is merged.

---

## Rollback procedures

The blast radius of a failed upgrade depends on **which phase failed** and **which method was chosen**. The matrix:

| Failed at | Method `--link` | Method `dumpall` |
|---|---|---|
| Phase 0–3 (pre-stop) | `./scripts/infra.sh up -d`. No state change. | Same. |
| Phase 4a step 1 (volume rename) | Reverse with the rollback snippet inside Phase 4a step 1's body — `find 16/docker -mindepth 1 -maxdepth 1 -exec mv {} /v/ \;` and `rmdir 16/docker 16`. Then `up -d`. | N/A (rename not done). |
| Phase 4a steps 2–3 (pg_upgrade ran, no PG process started yet) | Volume rename was done. Both PG16 and PG18 dirs exist; hard links share inodes; **but no PG cluster has written through them since the upgrade.** Reverse the rename in step 1, drop the PG18 subdir, `up -d`. | N/A. |
| Phase 4a step 4 (STOP marker — agent halted, awaiting human) | Same as steps 2–3 above; the cliff is not yet crossed because step 4 is non-actionable. | N/A. |
| Phase 4a step 5+ (new cluster started writing — `up -d` or `vacuumdb` ran) | **Hard rollback only.** Old cluster's files are now corrupted via shared hard links. Restore from `dumpall.before.sql`: see "Hard rollback" below. | N/A. |
| Phase 4b (new cluster started, restore failed mid-stream) | N/A. | Drop the PG18 volume, recreate, retry restore. The PG16 backup volume is still intact. |
| Phase 5–7 (new cluster verified bad post-upgrade) | "Hard rollback" via dumpall. | "Soft rollback": stop, swap volumes (`pg_data` ↔ `pg_data_pg16_backup`), re-pin compose to `postgres:16-bookworm`, `up -d`. |
| Phase 8 (post-merge regression) | Revert the Phase 2 commit + repeat hard rollback. | Revert the Phase 2 commit + repeat soft rollback. |

### Hard rollback (any state, including post-`--link` corruption)

1. Stop the stack: `./scripts/infra.sh down`.
2. Wipe the volume: `docker volume rm pg_data && docker volume create pg_data`.
3. Revert the Phase 2 PR (or `git checkout main` on a clean tree).
4. Bring up only the old PG16 image: `./scripts/infra.sh up -d postgres postgres-bootstrap`.
5. Restore the dump:
   ```bash
   docker compose exec -T postgres psql -U user -d postgres < ~/wealthpay-pg-upgrade-baseline/dumpall.before.sql
   ```
6. Bring the rest up: `./scripts/infra.sh up -d`.
7. Re-register the connector: `./debezium/register-connector.sh`.

**Data loss bound:** zero, if the dump in Phase 3 was taken after the slot drained. Any application writes that landed *after* the dump and *before* the stack stopped are not in the dump and are lost.

### Soft rollback (Phase 4b only — old volume preserved)

1. Stop the stack: `./scripts/infra.sh down`.
2. Swap volumes:
   ```bash
   docker volume rm pg_data
   docker volume create pg_data
   docker run --rm -v pg_data_pg16_backup:/from -v pg_data:/to alpine sh -c 'cp -a /from/. /to/'
   ```
3. Revert the Phase 2 PR.
4. `./scripts/infra.sh up -d`.
5. Re-register the connector.

**Data loss bound:** zero. The PG16 volume is byte-identical to the pre-upgrade state.

---

## Breaking-change matrix (per file)

Single source of truth for "what changes where". An agent assigned a single phase should be able to read the relevant rows here without re-deriving them from upstream release notes.

| File | Reason for change | PG version that introduced it | Phase |
|---|---|---|---|
| `docker/postgres/Dockerfile` | Base image bump; `postgresql-16-cron` package replaced by `postgresql-18-cron` | PG18 image release | 2.1 |
| `docker-compose.local.yml` (postgres volume) | PG18 image declares `VOLUME /var/lib/postgresql` and `PGDATA=/var/lib/postgresql/18/docker` | PG18 image release | 2.2a |
| `docker-compose.local.yml` (postgres-bootstrap image) | Wire-protocol client kept in lockstep with server | PG18 image release | 2.2b |
| `docker-compose.local.yml` (postgres-exporter command) | `pg_stat_bgwriter` checkpoint columns relocated to `pg_stat_checkpointer`; `--collector.stat_checkpointer` is opt-in | PG17 | 2.2c |
| `docker/sql-exporter/wal-io.collector.yml` | `pg_stat_wal` WAL-I/O columns relocated to `pg_stat_io WHERE object='wal'`; existing query fails at parse | PG18 | 2.4 |
| `docker/sql-exporter/replication.collector.yml` | `pg_replication_slots.inactive_since` column added; recording-rule fallback no longer needed | PG17 (column add); PG18 (carried forward) | 2.5 |
| `docker/prometheus/rules/db.rules.yml` (recording rule) | Removed; replaced by SQL-side metric | PG17+ | 2.6a |
| `docker/prometheus/rules/db.rules.yml` (`ReplicationSlotInactive`) | References the new SQL-side metric | PG17+ | 2.6b |
| `docker/prometheus/rules/db.rules.yml` (`DbForcedCheckpointRatioHigh`) | References the new `pg_stat_checkpointer_*` metrics | PG17 | 2.6c |
| `docker/grafana/dashboards/db-server-health.json` (panels #12, #13) | References new `pg_stat_checkpointer_*` PromQL | PG17 | 2.7 |
| `debezium/register-connector.sh` | Adds `snapshot.mode: never` (decided: production-strict, no duplicates) | N/A — operational choice (locked in) | 6.2 |
| `src/test/java/.../AbstractContainerTest.java` | Testcontainers image pin must match runtime `postgres:18` | PG18 image release | 2.0 |
| `pom.xml` | Conditional — Postgres JDBC driver override **only if** Spring Boot BOM resolves a version < 42.7.4 | PG18 wire-protocol features | 2.0 |
| `docs/postgres-upgrade.md` (header) | "Current stack version" updated | N/A — documentation | 8.4 |
| `docs/adr/008-db-observability-slos.md` (under-load baselines) | Re-sampled under PG18 with `io_method=worker` default | PG18 | 8.3 |
| `README.md` | Version mention | N/A — documentation | 2.8 |

---

## Appendix A — Verification commands quick reference

For an agent debugging a failed phase, the canonical "is this thing alive" probes:

```bash
# Postgres reachable, version reported correctly:
docker compose exec -T postgres psql -U user -d wealthpay -c "SELECT version();"

# Replication slot present, active, no excessive retention. Set SLOT_NAME first —
# under Path C (Phase 6 default) it's debezium_pg18; pre-upgrade or under Path A/B it's debezium.
: "${SLOT_NAME:=debezium}"
docker compose exec -T postgres psql -U user -d wealthpay -c \
  "SELECT slot_name, active, inactive_since, invalidation_reason,
          pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn) AS lag_bytes,
          pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS retention_bytes
     FROM pg_replication_slots
    WHERE slot_name = '${SLOT_NAME}';"

# pg_cron job still scheduled:
docker compose exec -T postgres psql -U user -d wealthpay -c \
  "SELECT jobid, schedule, command, active FROM cron.job;"

# Flyway state intact:
docker compose exec -T postgres psql -U user -d wealthpay -c \
  "SELECT installed_rank, version, success FROM flyway_schema_history ORDER BY installed_rank;"

# Eight WAL metrics flowing (the PG18 hazard zone):
curl -s localhost:9399/metrics | grep -E '^pg_wal_stat_' | wc -l   # expect: 8

# Four checkpointer metrics flowing (the PG17 hazard zone):
curl -s localhost:9187/metrics | grep -E '^pg_stat_checkpointer_' | wc -l   # expect: 4

# Debezium connector RUNNING:
# Set CONNECTOR_NAME first — under Path C (Phase 6 default) it's wealthpay-outbox-connector-pg18;
# pre-upgrade or under Path A/B it's wealthpay-outbox-connector.
: "${CONNECTOR_NAME:=wealthpay-outbox-connector}"
curl -sf "http://localhost:8083/connectors/${CONNECTOR_NAME}/status" \
  | jq '.connector.state, .tasks[].state'   # expect: "RUNNING" "RUNNING"

# All Prometheus scrape targets healthy:
curl -sG 'http://localhost:9090/api/v1/query' --data-urlencode 'query=up' \
  | jq '.data.result[] | select(.value[1] != "1")'   # expect: empty

# No alerts in NoData state:
curl -sG http://localhost:9090/api/v1/rules \
  | jq '.data.groups[].rules[] | select(.health != "ok")'   # expect: empty
```

---

## Appendix B — Decision log

Decisions taken before plan execution. Locked in 2026-04-30 by the human owner; agents read these as constraints, not as defaults.

| # | Question | Decision | Rationale |
|---|---|---|---|
| 1 | Method (Phase 1) | **`--link`** | Learning project — production-realism wins over local-convenience; planner-statistics preservation is a real bonus. |
| 2 | CDC duplicate tolerance (Phase 6.2) | **`snapshot.mode=never`** | Production-strict; the downstream idempotency exists but should not be relied on as the load-bearing layer for a controlled upgrade. |

### Still open

These remain unresolved and need human input before the relevant phase fires. Agents executing those phases must escalate, not assume.

1. **postgres-exporter version (Phase 2).** Stay on v0.19.1, or take the upgrade window as an opportunity to bump to the latest? Default recommendation is STAY — the version-bump is a parallel workstream and conflating them is a known way to silently break unrelated metrics. Re-engage if [Phase 5](#phase-5--bring-up-new-stack--verify-metric-pipeline) surfaces a missing PG18 collector.
2. **Production rehearsal cadence.** When does the production stack go through this? The local upgrade should be a complete dry run, but the production runbook will need its own owner and its own tickets. Open a separate ticket once Phase 8 closes.

---

## Appendix C — Agent prompts library (copy-paste ready)

> **Workflow.** The human owner invokes one agent per phase, reviews the deliverable, then invokes the next. Each prompt below is **self-contained for a fresh subagent** — fork agents (with no `subagent_type`) inherit your context, but the typed agents below (`java-backend-architect`, `general-purpose`, `code-reviewer`) start with zero conversation memory and need the full briefing inline. Every prompt names its phase boundary explicitly so the agent stops where you can take over.
>
> **How to use.** Copy a code block, invoke the named agent type with it as the `prompt`, wait for the deliverable, review against the phase's acceptance criterion (in the plan body above), then move to the next prompt.

---

### D.0 — Phase 0 (`general-purpose`)

```
You are picking up Phase 0 of a PostgreSQL 16 → 18 migration plan in the
Wealthpay repository. The plan is at docs/postgres-18-migration-plan.md.
Read the entire "Phase 0 — Pre-flight inventory & baseline" section before
starting anything; it is the source of truth for the steps.

CONTEXT: this is a learning project; the human owner is supervising each
phase manually. You own ONLY Phase 0. Do NOT proceed into any other phase
even if you finish early.

JOB: execute Phase 0's seven numbered steps end-to-end. The work is purely
read-only — capture metric inventories, row counts, slot state, Flyway
state, and active alerts to ~/wealthpay-pg-upgrade-baseline/.

DELIVERABLE: a short markdown summary covering:
  - all baseline files created (path + size + first/last 3 lines of each)
  - the eight pg_wal_stat_* metric names + values observed
  - the four pg_stat_bgwriter_checkpoint* metric names + values observed
  - replication slot lag bytes + active state
  - row counts for the seven tables in step 5

STOP CONDITIONS — return immediately and report:
  - replication slot inactive, OR lag > 50 MB
  - fewer than 8 pg_wal_stat_* or 4 pg_stat_bgwriter_checkpoint* metrics
  - any verification step in the plan section fails

DO NOT: edit any file, push git, mutate the cluster, register/deregister
Debezium connectors, run pg_dumpall (that is Phase 3, owned by a different
agent invocation).

Report when done.
```

---

### D.2.0 — Phase 2.0 Java compatibility audit (`java-backend-architect`)

```
You are picking up Phase 2.0 of a PostgreSQL 16 → 18 migration plan in the
Wealthpay repository. Plan is at docs/postgres-18-migration-plan.md.
Read the entire "Phase 2.0 Java-side compatibility audit" subsection (under
Phase 2) before doing anything; it has exact file paths and line numbers.

CONTEXT: learning project, human-supervised, decisions locked in. You own
ONLY the Java-side track of Phase 2 (sub-phase 2.0). The infra track (2.1
through 2.8) is owned by a different agent invocation — do NOT touch any
file under docker/, docker-compose*.yml, src/main/resources/, or
src/main/generated-jooq/.

JOB: the five numbered steps in Phase 2.0:
  1. Pin Testcontainers to postgres:18 in
     src/test/java/.../AbstractContainerTest.java line 14
  2. Audit pom.xml: produce a 5-row table of (library, resolved version,
     PG18 verdict) covering Postgres JDBC driver (use mvn dependency:tree),
     Flyway, jOOQ, Spring Boot, Testcontainers
  3. Decide on jOOQ regeneration. Default per the plan is DEFER. Document
     the decision in the PR description body.
  4. Run `mvn clean install` end-to-end. The integration tests must pass
     against the PG18 Testcontainers image.
  5. Run a manual smoke test: ./scripts/infra.sh up -d (still PG16 at this
     point) then mvn spring-boot:run, confirm clean Spring Boot startup
     and Flyway reports nothing-to-do.

DELIVERABLE: one git commit on a new feature branch named
`chore/pg18-java-compat`, containing the AbstractContainerTest.java change
plus any conditional pom.xml override. Commit message follows the existing
repo style (check git log for tone). The PR description draft (write to a
new file PR_DESCRIPTION.md at repo root, do not commit it — leave it
uncommitted for the human to copy into the eventual PR) must include:
  - the dependency-version audit table from step 2
  - the jOOQ regen decision and rationale from step 3
  - the mvn clean install summary (test count, pass/fail)

STOP CONDITIONS — return immediately and escalate:
  - mvn clean install fails. Do NOT mock or skip failing tests.
  - resolved Postgres JDBC driver < 42.7.4 — flag this and propose the
    explicit override in the PR description, but commit the override only
    if you are confident it does not conflict with Spring Boot 4.0.2's BOM.
  - any test fails specifically because of a jOOQ generated-class
    incompatibility — this overrides the "defer regen" default; escalate.

DO NOT: bump Spring Boot / Confluent / jOOQ versions; push the branch;
merge anything; touch any non-Java/non-pom file; register or deregister
the Debezium connector; modify the running stack.

Run the work, commit, leave PR_DESCRIPTION.md uncommitted at repo root,
report back.
```

---

### D.2.infra — Phase 2.1 through 2.8 infrastructure track (`general-purpose`)

```
You are picking up the infrastructure track of Phase 2 in a PostgreSQL 16
→ 18 migration plan. Plan is at docs/postgres-18-migration-plan.md. Read
ALL of Phase 2 (sub-phases 2.1 through 2.8) plus the entire companion
document docs/postgres-upgrade.md — that is the source of truth for the
fix recipes in 2.4 and 2.6.

CONTEXT: learning project, human-supervised. Decisions locked in: Method
is pg_upgrade --link, Debezium will use snapshot.mode=never. Do NOT
re-litigate. The Java track (Phase 2.0) is owned by a different agent
invocation and may have already produced a `chore/pg18-java-compat`
branch — check `git branch` first; reuse it if it exists, otherwise create
it.

JOB: edit eight file groups per the plan:
  2.1 docker/postgres/Dockerfile (image bump + pg_cron package rename)
  2.2 docker-compose.local.yml (THREE edits: volume mount path, bootstrap
      image, --collector.stat_checkpointer flag — see 2.2a/b/c)
  2.3 docker-compose.local.linux.yml (verify no change required)
  2.4 docker/sql-exporter/wal-io.collector.yml (split queries; this is
      load-bearing for R2 — read postgres-upgrade.md §PG18)
  2.5 docker/sql-exporter/replication.collector.yml (add
      inactive_since_seconds; the plan flags an implementation note about
      sql-exporter's value-column contract — read it)
  2.6 docker/prometheus/rules/db.rules.yml (drop recording rule + update
      two alerts)
  2.7 docker/grafana/dashboards/db-server-health.json (panels #12 and
      #13: bgwriter → checkpointer find-and-replace; verify
      db-query-performance.json and account-service.json have no
      occurrences)
  2.8 README.md (version mention)

DELIVERABLE: one commit on `chore/pg18-java-compat` (or whichever branch
the Java track used). PR description (write/append to PR_DESCRIPTION.md
at repo root, leave uncommitted) must include:
  - bullet list of every metric name that changes after upgrade
  - output of: mvn clean install
  - output of: docker buildx build docker/postgres/
  - output of: promtool check rules docker/prometheus/rules/db.rules.yml
    (use `docker run --rm -v "$PWD/docker/prometheus/rules:/rules"
    prom/prometheus:v3.10.0 promtool check rules /rules/db.rules.yml`)

STOP CONDITIONS — return immediately and escalate:
  - apt-get install postgresql-18-cron fails inside the new Dockerfile
    build — may need to add the PGDG repo to the postgres:18-trixie
    base; flag and ask
  - the pg_stat_io WAL backend_type for sub-phase 2.4 cannot be confirmed
    against upstream PG18 docs — the plan flags this as the load-bearing
    assumption for 2.4; do NOT guess
  - promtool reports any rule error
  - mvn clean install fails (the Java track already validated this; if
    you broke it, something in your edits crossed scope)

DO NOT: edit any *.java file or pom.xml (Java track scope); run
pg_upgrade or pg_dumpall; stop the cluster; register or deregister
Debezium; push the branch; merge anything.

Run edits, commit, append to PR_DESCRIPTION.md, report back with the four
verification outputs.
```

---

### D.2.9 — Phase 2.9 pg-upgrader image rehearsal (`general-purpose`, human approves before Phase 3)

```
You are picking up Phase 2.9 of a PostgreSQL 16 → 18 migration plan in
the Wealthpay repository. Plan is at docs/postgres-18-migration-plan.md;
read "Phase 2.9 — Build and rehearse the pg-upgrader image" top to
bottom before starting.

CONTEXT: learning project, human-supervised. Phase 2 PR is on the
feature branch. Decisions locked in: --link, snapshot.mode=never. Phase
2.9 is a BLOCKING gate before Phase 3 — Phase 3 must NOT start until
this phase passes and a human signs off.

JOB: the nine numbered steps in Phase 2.9 (0 through 8) verbatim.
  0. Volume-name preflight — `docker volume inspect pg_data` must
     succeed. If it does not, STOP and resolve the actual Compose
     volume name; do NOT improvise.
  1. Build (or pull) the pg-upgrader image (default: hand-rolled, based
     on postgres:18-trixie with postgresql-16, postgresql-16-cron,
     postgresql-18-cron added).
  2. Create a disposable rehearsal volume from the real pg_data volume
     via cp -a. The real volume must remain untouched.
  3. Run the same volume-layout migration as Phase 4a step 1 against
     the rehearsal volume.
  4. Initialize the PG18 target datadir via initdb. Match
     encoding/locale to the source cluster (capture from PG16
     pg_controldata if defaults shown in the plan don't match).
  5. Run pg_upgrade --check (NOT --link) first.
  6. Run pg_upgrade --link against the rehearsal volume; capture
     stdout, stderr, exit status.
  7. Start the rehearsed PG18 cluster using the SAME custom image
     (wealthpay-postgres-local) and SAME GUCs as runtime. Verify
     version, row counts, pg_extension, and SHOW shared_preload_libraries.
     Do NOT use bare postgres:18-trixie.
  8. Destroy the rehearsal volume.

DELIVERABLE: a markdown report covering:
  - the upgrader image tag and build log (or pull log if reused)
  - pg_upgrade --check exit status + relevant output
  - pg_upgrade --link exit status + log tail
  - the SELECT version() and row-count outputs from the rehearsed
    cluster
  - confirmation that the cp source mount in step 2 used :ro (the
    load-bearing safety) and that the alpine ls -la snapshot of
    pg_data before vs after Phase 2.9 matches as a sanity check
  - "READY FOR HUMAN: pg-upgrader image rehearsed successfully against
    a disposable copy; Phase 3 can proceed" line as the last item

STOP CONDITIONS — return immediately and escalate:
  - step 0 preflight fails (volume name mismatch) — STOP, do not
    improvise the volume name.
  - step 4 initdb fails — the upgrader image cannot create the new
    cluster; do NOT proceed to step 5.
  - pg_upgrade --check exits non-zero — the upgrader image is not
    ready; do NOT proceed to step 6.
  - pg_upgrade --link exits non-zero — the rehearsal failed; do NOT
    proceed to Phase 3 under any circumstances.
  - the rehearsed cluster does not start, or pg_cron is missing, or
    SHOW shared_preload_libraries does not include pg_cron.
  - the real pg_data volume is touched in any way.

DO NOT, UNDER ANY CIRCUMSTANCES: run anything against the real pg_data
volume; stop the production cluster; take a pg_dumpall (that is
Phase 3); register or deregister Debezium connectors.

Report and stop.
```

---

### D.3 — Phase 3 drain & backup (`general-purpose`)

```
You are picking up Phase 3 of a PostgreSQL 16 → 18 migration plan. Plan
is at docs/postgres-18-migration-plan.md; read "Phase 3 — Drain & backup"
top to bottom before starting.

CONTEXT: learning project, human-supervised. The Phase 2 PR has landed on
a feature branch but not merged. The cluster is still PG16. You are the
last read-only/non-destructive phase before the actual upgrade in Phase 4a
— BUT you DO touch cluster state (pause the connector, take a dump, stop
the stack). No code edits.

PRE-CHECK: confirm the human owner has reviewed Phase 0's deliverable
and Phase 2's PR — if there is no PR yet, STOP and ask before proceeding.

JOB: the seven numbered steps in Phase 3 verbatim. NOTE THE ORDER —
the connector must be RUNNING (not paused) during the drain wait,
otherwise lag does not converge.
  1. Confirm the application is stopped (no mvn spring-boot:run process)
  2. Wait — actively poll until lag_bytes is 0 or single digits, while
     the connector is still RUNNING. Do NOT pause first.
  3. Pause the Debezium connector (PUT /pause) — only after drain
     converges.
  4. Take pg_dumpall to ~/wealthpay-pg-upgrade-baseline/dumpall.before.sql
  5. DELETE the connector
  6. Drop the 'debezium' replication slot explicitly via
     pg_drop_replication_slot — defence in depth. Idempotent via the
     WHERE clause in the plan body.
  7. ./scripts/infra.sh stop  (stop, NOT down -v)

DELIVERABLE: a short markdown summary covering:
  - dumpall.before.sql size + line count + per-table presence check
    (the for-loop in Phase 3 acceptance criterion that greps for each
    of the seven contract tables — output must be empty)
  - confirmation that the connector returns 404
  - confirmation that pg_replication_slots no longer contains 'debezium'
  - confirmation that all containers are in Exited state
  - the per-contract-table presence loop output (Phase 3 acceptance
    criterion) — empty output, confirming every preserved table has a
    CREATE TABLE statement in dumpall.before.sql

STOP CONDITIONS — return immediately and escalate:
  - lag_bytes does not reach near-zero within ~5 minutes (consumer is
    stuck — the upgrade cannot proceed safely)
  - pg_dumpall errors or produces a file < 1 MB
  - any compose container fails to stop cleanly

DO NOT: bring up the new PG18 stack (Phase 4a); run pg_upgrade; merge
the PR; register a new connector; touch the named volume in any way.

Report when done.
```

---

### D.4a-prep — Phase 4a steps 1–4 (`general-purpose`, agent-safe portion)

```
You are picking up the AGENT-SAFE PORTION of Phase 4a of a PostgreSQL 16 →
18 migration plan. Plan is at docs/postgres-18-migration-plan.md; read
"Phase 4a — Execute pg_upgrade --link" top to bottom.

CRITICAL — irreversibility boundary: Phase 4a step 5 ("./scripts/infra.sh
up -d" — bring the new cluster up) is the point past which the old PG16
cluster cannot be recovered without the dumpall safety net (hard links
shared via --link). YOU OWN STEPS 1 THROUGH 3 ONLY. Step 4 is a STOP
marker (no action required). After step 3, STOP and report. The human
runs steps 5 and 6 (cluster up + vacuumdb). DO NOT run vacuumdb in any
form, including via a transient container — vacuumdb requires a running
Postgres against the new layout, which crosses the cliff.

CONTEXT: learning project, human-supervised. Phase 3 has stopped the
stack, taken the dumpall safety net, and dropped the Debezium connector.
The Phase 2 PR is on a feature branch (not merged).

PRE-CHECK: confirm ~/wealthpay-pg-upgrade-baseline/dumpall.before.sql exists and
is > 1 MB. If not, STOP — the rollback substrate is missing.

JOB: the first three numbered steps in Phase 4a (step 4 is a STOP marker).
  1. One-time volume-layout migration. Use the multi-line shell snippet
     from Phase 4a step 1 verbatim — DO NOT use the older single-line
     `mv /v/data /v/16/docker` form (it assumed the volume mounted at
     the parent of PGDATA, which is wrong for this repo: the volume
     root IS the data directory). The snippet has a built-in pre-check
     that aborts if /v/16 or /v/18 already exists or if /v/PG_VERSION
     is missing, so it is safe to re-invoke; if either pre-check fires,
     STOP and surface the failure rather than working around it.
  2. Run pg_upgrade in a transient container. Default to Option B (hand-
     rolled image with explicit --link flag) — Option A's pgautoupgrade
     image has an opinionated entrypoint and the --link mode is governed
     by env vars, not a passthrough flag. If Option A is used, verify
     against the image's README first; do not assume the CLI surface.
     Capture stdout AND stderr to a log file.
  3. Verify exit status 0, both /v/16/docker and /v/18/docker exist
     (alpine ls inside the volume), and the new cluster's locale and
     encoding match the old. Use file-only inspection — `pg_controldata`
     reads the cluster control file without starting Postgres, so the
     irreversibility cliff is not crossed:
       docker run --rm -v pg_data:/v ghcr.io/<org>/pg-upgrader:18 \
         /usr/lib/postgresql/18/bin/pg_controldata /v/18/docker
       docker run --rm -v pg_data:/v ghcr.io/<org>/pg-upgrader:18 \
         /usr/lib/postgresql/16/bin/pg_controldata /v/16/docker
     Compare the "LC_COLLATE", "LC_CTYPE", and "Database block size"
     lines between the two outputs. SQL-based verification (e.g.
     SELECT datcollate FROM pg_database) belongs AFTER the human-owned
     start in step 5 — running it now would require booting PG18,
     which crosses the boundary. (Earlier drafts of this prompt had a
     `postgres -D /v/18/docker -c "SELECT ..."` line; that is wrong on
     two counts: `-c` sets a GUC not SQL, and it would start the
     cluster anyway. Do not use it.)

DELIVERABLE: a markdown report covering:
  - the pg_upgrade exit status
  - the full pg_upgrade log (or a clearly-marked tail if too long)
  - the alpine ls output showing both directory trees
  - explicit "READY FOR HUMAN: run `./scripts/infra.sh up -d` to start
    the new cluster, then `docker compose exec -T postgres vacuumdb -U
    user --all --analyze-in-stages --missing-stats-only` for the
    post-upgrade analyze" line as the last item

STOP CONDITIONS — return immediately and escalate:
  - pg_upgrade exits non-zero. The rollback at this point is the
    multi-step `find ... -exec mv ... \;` snippet documented in
    Phase 4a step 1's body (move /v/16/docker contents back to /v/, then
    rmdir 16/docker 16). Do NOT run it without human approval — surface
    the failure first. Do NOT use the older single-line `mv /v/16/docker
    /v/data` form (it does not match the actual volume layout).
  - pgautoupgrade/pgautoupgrade:18-trixie image is unavailable for the
    host architecture. Escalate; do NOT fall back to dump/restore.
  - vacuumdb fails (likely indicates the upgrade left the cluster in a
    broken state)

DO NOT, UNDER ANY CIRCUMSTANCES: run ./scripts/infra.sh up -d, ./scripts/
infra.sh up postgres, docker compose up; start any long-running PG
container against /v/18/docker. Step 5 is human-only. After step 4, your
job ends.

Report and stop.
```

---

### D.4.5 — Phase 4.5 post-upgrade data preservation (`general-purpose`)

```
You are picking up Phase 4.5 of a PostgreSQL 16 → 18 migration plan.
Plan is at docs/postgres-18-migration-plan.md; read "Phase 4.5 —
Post-upgrade data preservation check" top to bottom before starting.

CONTEXT: learning project, human-supervised. The human has just brought
up the PG18 stack (Phase 4a step 5) and run vacuumdb (Phase 4a step 6).
Application is NOT running. Debezium connector is NOT registered. No
new write has hit the new cluster.

THIS PHASE IS THE ONLY PLACE WHERE STRICT R1 (data preservation) IS
PROVED. After Phase 6 starts the application and Phase 7 runs Gatling,
row counts will legitimately diverge — that is not a regression, it is
the writer doing its job. If you do not catch a row-count mismatch
HERE, the upgrade may have lost data and Phase 7 cannot detect it.

JOB: the four numbered steps in Phase 4.5 verbatim.
  1. SELECT version() returns PG18.
  2. flyway_schema_history diff against Phase 0 baseline is empty.
  3. row-counts.preserved.after.initial diff against
     row-counts.preserved.before is empty (strict). row-counts.outbox.*
     is captured separately and is informational only — DO NOT fail on
     an outbox delta.
  4. cron.job_run_details is inspected to explain any preserved-table
     count delta as pg_cron-driven (not data loss).

DELIVERABLE: a markdown report covering:
  - the SELECT version() output
  - the flyway diff (must be empty)
  - the row-counts diff (must be empty for the six preserved tables)
  - any pg_cron job-run output that explains a delta, if applicable
  - explicit "R1 PROVED — Phase 5/6/7 may proceed" or "R1 FAILED —
    rollback required" line

STOP CONDITIONS — return immediately and escalate:
  - any preserved-table row count differs and step 4 cannot explain it
    via pg_cron — escalate immediately. Hard rollback via
    dumpall.before.sql is the recovery path; do NOT proceed.
  - flyway_schema_history rows are missing — same.
  - SELECT version() does not report PG18 — the upgrade is incomplete;
    do NOT proceed.

DO NOT: register the Debezium connector (Phase 6); start the
application; run Gatling (Phase 7); modify any configuration; mutate
any row in the database. This phase is read-only.

Report when done.
```

---

### D.5 — Phase 5 verify metric pipeline (`general-purpose`)

```
You are picking up Phase 5 of a PostgreSQL 16 → 18 migration plan. Plan
is at docs/postgres-18-migration-plan.md; read "Phase 5 — Bring up new
stack & verify metric pipeline" top to bottom. Companion doc
docs/postgres-upgrade.md is the source of truth for the metric-relocation
fix recipes — keep it open in case a metric is missing.

CONTEXT: learning project, human-supervised. The human has just brought
up the PG18 stack (Phase 4a step 5). Application is not yet running.
Debezium connector is not yet registered. You verify the metric pipeline
ONLY.

JOB: the eight numbered steps in Phase 5 verbatim.

DELIVERABLE: a markdown report covering:
  - diff of ~/wealthpay-pg-upgrade-baseline/postgres-exporter.before vs .after
  - diff of ~/wealthpay-pg-upgrade-baseline/sql-exporter.before vs .after
  - the eight pg_wal_stat_* lines (raw curl output)
  - the four pg_stat_checkpointer_* lines
  - Prometheus `up` query result for postgres jobs
  - Prometheus rules health check (no nodata)
  - Grafana visual verification: per-panel pass/fail for db-server-health
    panels #8–#13, db-query-performance, and account-service. If you
    cannot reach Grafana, say so explicitly — do NOT skip silently.

EXPECTED DIFFS (this is the contract for Phase 5):
  - postgres-exporter: -4 pg_stat_bgwriter_checkpoint* +4
    pg_stat_checkpointer_* +N pg_stat_io_*_bytes_total (PG18 new)
  - sql-exporter: 0 diff for pg_wal_stat_*; +1
    pg_replication_slots_inactive_since_seconds

Any other diff item is a regression — call it out.

STOP CONDITIONS — return immediately and escalate:
  - any pg_wal_stat_* metric missing — point at sub-phase 2.4 fix recipe
  - any pg_stat_checkpointer_* metric missing — point at the
    --collector.stat_checkpointer flag in 2.2c
  - any Prometheus scrape target down (up != 1)
  - any Grafana panel says "No data"

DO NOT: register the Debezium connector (Phase 6); start the application;
run pg_upgrade or pg_dumpall; modify any configuration. If a metric is
missing, REPORT — do not patch.

Report when done.
```

---

### D.6 — Phase 6 recreate Debezium (`general-purpose`)

```
You are picking up Phase 6 of a PostgreSQL 16 → 18 migration plan. Plan
is at docs/postgres-18-migration-plan.md; read "Phase 6 — Recreate
Debezium connector" top to bottom.

CRITICAL: the chosen contract is snapshot.mode=never. The OPERATIONAL
SEQUENCE is load-bearing — register the connector BEFORE starting the
application. If you reverse the order, events written between app-start
and connector-register are silently dropped. The plan's 6.3 spells this
out.

CONTEXT: learning project, human-supervised. PG18 cluster is up. Metric
pipeline verified by Phase 5. Application is not yet running.

JOB: the eight numbered steps (0 through 7) in Phase 6 in order. Step 0
is the new Kafka Connect offset pre-check — do NOT skip it; the chosen
offset path must be documented in the PR description before
re-registering the connector.
  1. Verify dbz_publication exists; re-run V15/V16 if absent
  2. Edit debezium/register-connector.sh — add "snapshot.mode": "never"
     to the JSON config block exactly per the plan's 2-line diff
  3. Re-register the connector via the script (DELETE + POST)
  4. Confirm RUNNING within 30s
  5. Confirm slot is active with low retention
  6. Start the application: mvn spring-boot:run (run in background; the
     Spring Boot startup output is part of your deliverable)
  7. Smoke test: POST /api/v1/accounts then verify the new account
     appears in account.account_balance_view within 5 seconds

DELIVERABLE: a markdown report covering:
  - the diff applied to register-connector.sh
  - the connector status output after registration
  - the slot state after registration
  - the smoke-test POST request body, response, and the
    account_balance_view query result

STOP CONDITIONS — return immediately and escalate:
  - dbz_publication is missing AND V15/V16 cannot be re-run cleanly
  - connector returns FAILED with "replication slot does not exist" —
    the plan's "On failure" block has the recovery; if it does not work,
    escalate
  - connector RUNNING but smoke-test event does not appear in
    account_balance_view within 30s — the CDC chain is broken; do NOT
    proceed to Phase 7 verification

DO NOT: run the snapshot-everything path (snapshot.mode=initial — the
plan rejects it); merge the Phase 2 PR; modify the application code; run
mvn clean install (Java compat is already validated).

Commit the register-connector.sh edit on the same feature branch as the
rest of Phase 2. Report when done.
```

---

### D.7 — Phase 7 final verification (`general-purpose`, then `code-reviewer`)

```
[Run as `general-purpose` first, then independently as `code-reviewer`
with the same prompt body — the second invocation gives you an
independent read of the diffs before the merge.]

You are picking up Phase 7 of a PostgreSQL 16 → 18 migration plan. Plan
is at docs/postgres-18-migration-plan.md; read "Phase 7 — Final
verification" top to bottom.

CONTEXT: learning project, human-supervised. PG18 cluster live;
application running; CDC restored. The PR is ready for merge but NOT
merged. R1 (data preserved) was already proved in Phase 4.5 *before*
any new write occurred — Phase 7 inherits that result and does NOT
re-run the strict row-count diff (it would fail-by-design after the
Phase 6 smoke test and Phase 7's Gatling spike). Phase 7 proves R2
(metrics behaviour preserved) and R3 (no silent CDC data loss).

JOB: the four numbered verification steps in Phase 7 verbatim.

DELIVERABLE: a "Verification" markdown block to be appended to
PR_DESCRIPTION.md at repo root (leave uncommitted), covering:
  - R1: cite the Phase 4.5 result (do NOT re-run the row-count diff
    here — it is expected to differ after smoke test + Gatling)
  - R2: postgres-exporter.before vs .after AND sql-exporter.before vs
    .after — the full diffs, inline
  - R3: Gatling pass/fail summary + post-Gatling event_store and
    account_balance_view counts; AND the projection-consistency
    invariant from Phase 7 step 2 (count(distinct account_id) from
    event_store == count(*) from account_balance_view)
  - R3: replication-slot lag and retention bytes after the Gatling spike
  - alerts.before vs .after

STOP CONDITIONS — return immediately and escalate:
  - the Phase 4.5 deliverable is missing or did not prove R1. Phase 7
    inherits R1 from Phase 4.5; if Phase 4.5 was not run or did not
    pass, Phase 7 cannot proceed. Do NOT re-derive R1 here — Phase 6's
    smoke test and this phase's Gatling spike both legitimately mutate
    the preserved tables, so a row-count diff would fail-by-design.
  - the cutover-anchored projection invariant fails:
    `post_cutover_aggregates_missing_from_projection > 0` (any aggregate
    whose FIRST event landed after pg_postmaster_start_time is missing
    from account_balance_view). This is a silent CDC drop on the new
    cluster and is the load-bearing R3 check. The strict-equality form
    may be unequal due to pre-cutover legacy drift faithfully preserved
    by pg_upgrade --link — investigate via per-aggregate date forensics
    and document; do NOT treat as a stop condition unless a post-cutover
    aggregate is among the misses.
  - any unexpected metric series diff (one not in the expected set
    spelled out in Phase 5)
  - any new firing alert that wasn't firing before
  - replication slot lag > 100 MB or retention > 1 GiB

If invoked as code-reviewer (second pass): independently run the same
four checks, then read the PR_DESCRIPTION.md draft and the diffs of all
files changed in the feature branch. Comment on anything that looks
wrong, ambiguous, or under-tested. Do NOT take the report at face value;
re-derive the metrics from scratch.

DO NOT: merge the PR (Phase 8); push the branch; modify the application;
modify the metrics pipeline.

Report when done.
```

---

### D.8 — Phase 8 re-baseline & merge (`general-purpose`, agent-safe portion)

```
You are picking up the AGENT-SAFE PORTION of Phase 8 of a PostgreSQL 16
→ 18 migration plan. Plan is at docs/postgres-18-migration-plan.md; read
"Phase 8 — Re-baseline ADR-008 & merge" top to bottom.

IRREVERSIBILITY BOUNDARY: the actual `git merge` and `git push` to main
is HUMAN-ONLY. You prepare the diffs and the updated documents; the
human runs the merge.

CONTEXT: learning project, human-supervised. Phase 7 has produced the
verification report. The PR is ready for merge.

JOB: the four numbered steps in Phase 8.
  1. Run mvn -Pgatling gatling:test for the calibration workload;
     capture pass/fail and p50/p95/p99/max
  2. Sample under-load metrics during the run (cache-hit ratio, WAL
     retention, checkpointer rates)
  3. Edit docs/adr/008-db-observability-slos.md per the plan's bullets
     (SLI inventory under-load baseline column; Calibration log new
     row dated today; cache-hit-ratio re-baseline check; PG16 reference
     in "Why inactive_age_seconds is a recording rule" updated)
  4. Edit docs/postgres-upgrade.md per the plan's bullets (header
     version line; mark PG17/PG18 hazard sections as Applied)

DELIVERABLE: one final commit on the feature branch with the ADR and
runbook edits. Then write a 3-sentence summary at the BOTTOM of
PR_DESCRIPTION.md saying:
  - Gatling baseline numbers
  - which ADR-008 baselines moved (if any)
  - "READY FOR HUMAN: review PR_DESCRIPTION.md, then `gh pr create` and
    `gh pr merge` per repo conventions"

STOP CONDITIONS — return immediately and escalate:
  - Gatling fails (any assertion failure)
  - the under-load cache-hit-ratio drops below 0.95 — this triggers
    ADR-008's own re-baseline rule; flag it explicitly
  - any ADR section the plan mentions is structurally missing (someone
    has edited the ADR since this plan was written)

DO NOT, UNDER ANY CIRCUMSTANCES: run `gh pr merge`, `git merge`, or
`git push` of any kind. Do NOT delete the feature branch. Do NOT mark
anything as "shipped". Step 5 (the merge) is human-only.

Report when done.
```

---

## Appendix D — Sources

PG18 release notes and breaking changes:
- [PostgreSQL 18 release notes](https://www.postgresql.org/docs/current/release-18.html)
- [PostgreSQL 18 release announcement](https://www.postgresql.org/about/news/postgresql-18-released-3142/)
- [pg_stat_io WAL relocation](https://neon.com/postgresql/18/pg-stat-io)
- [pg_replication_slots inactive_since / invalidation_reason](https://www.postgresql.org/docs/current/view-pg-replication-slots.html)
- [Asynchronous I/O default behaviour](https://pganalyze.com/blog/postgres-18-async-io)
- [pg_upgrade preserves planner statistics from PG14+](https://postgres.ai/blog/20260324-pg18-stats-upgrade-across-versions)

pg_upgrade --link constraints:
- [pg_upgrade documentation](https://www.postgresql.org/docs/current/pgupgrade.html)
- [Logical replication slot preservation requires source ≥ PG17](https://www.postgresql.org/docs/current/logical-replication-upgrade.html)
- [pgEdge: preserving replication slots across major versions](https://www.pgedge.com/blog/preserving-replication-slots-across-major-postgres-versions-postgresql-high-availability-for-major-upgrades)

Docker image breaking change:
- [docker-library/postgres PR #1259 — PGDATA layout in PG18+](https://github.com/docker-library/postgres/pull/1259)
- [Aron Schueler — fixing the PG18 PGDATA error in Docker Compose](https://aronschueler.de/blog/2025/10/30/fixing-postgres-18-docker-compose-startup/)
- [pgautoupgrade Docker image](https://github.com/pgautoupgrade/docker-pgautoupgrade)
- [tianon/docker-postgres-upgrade](https://github.com/tianon/docker-postgres-upgrade)

Tooling compatibility:
- [pg_cron releases — Debian apt postgresql-18-cron](https://apt.postgresql.org/pub/repos/apt/pool/main/p/pg-cron/)
- [prometheus-community/postgres_exporter — tested with PG13–18](https://github.com/prometheus-community/postgres_exporter)
- [Debezium PostgreSQL connector — recovering after pg_upgrade](https://debezium.io/documentation/reference/stable/connectors/postgresql.html)
