-- Register pg_stat_statements for per-query latency and I/O attribution.
-- Idempotent: safe to re-run on already-initialized volumes.
--
-- RUNTIME CONTRACT — read this before assuming this script "always runs":
-- Files under /docker-entrypoint-initdb.d/ execute ONLY on initial DB
-- initialization (PGDATA empty at container start). On the pg_upgrade --link
-- path the new cluster's PGDATA is pre-initialized, so this script does NOT
-- run during the upgrade — the equivalent ALTER EXTENSION ... UPDATE step
-- must be applied manually as part of the cutover (see the ALTER below for
-- the same logic, and docs/postgres-upgrade.md § "Upgrade procedure" for
-- where this fits in the major-version-bump checklist).
-- Coverage matrix:
--   * fresh `./scripts/infra.sh up`  → this script runs                ✓
--   * pg_upgrade --link cutover      → manual ALTER (mirror of below)  ✓ (not here)
--   * pg_dumpall + restore           → this script runs on init        ✓
--
-- The extension ships with the stock postgres:18 image (contrib), so no
-- Dockerfile change is required. It still must be loaded via
-- shared_preload_libraries in docker-compose.local.yml — without that, the
-- CREATE EXTENSION below will succeed syntactically but the view will be
-- empty because the query-tracking hook is never installed.
--
-- Read access: the `monitoring` role created in 02-monitoring-role.sql
-- inherits pg_monitor, which transitively grants pg_read_all_stats. Since
-- PG14, pg_read_all_stats is sufficient to SELECT from pg_stat_statements
-- (including query text). No additional GRANT is needed here.

-- Sanity check: refuse to proceed if shared_preload_libraries has been set
-- WITHOUT pg_stat_statements while the extension already exists in the DB.
-- That combination produces a "dormant" extension: the view is defined,
-- CREATE EXTENSION IF NOT EXISTS no-ops, but no queries are ever recorded.
-- Fails loudly with an actionable remedy instead of shipping empty metrics.
DO $$
DECLARE
  preload text;
BEGIN
  SELECT setting INTO preload
    FROM pg_settings
   WHERE name = 'shared_preload_libraries';

  IF preload NOT LIKE '%pg_stat_statements%' THEN
    RAISE EXCEPTION
      'pg_stat_statements is NOT in shared_preload_libraries (actual: %). '
      'The extension''s query-tracking hook is not installed; any CREATE '
      'EXTENSION call would silently produce an empty view. Run: '
      '`docker compose -f docker-compose.local.yml up -d --force-recreate postgres`',
      preload;
  END IF;
END
$$;

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- After pg_upgrade, the extension is preserved at the OLD cluster's installed
-- version (e.g. 1.10 from PG 16) even though the new server ships a newer
-- default_version (1.12 on PG 18). The view definition is then out of sync
-- with what postgres-exporter v0.19.1+ queries: it expects the 1.11 split of
-- blk_*_time into shared_blk_*_time / local_blk_*_time, so every scrape errors
-- with `column pg_stat_statements.shared_blk_read_time does not exist`.
--
-- Idempotency: only run ALTER EXTENSION ... UPDATE when there is actual drift
-- between installed_version and default_version. A bare ALTER would succeed on
-- every bootstrap but emit `NOTICE: version "X" already installed`, which is
-- log noise on the steady-state path (this script runs on every container
-- start). The version check below makes the no-op path silent and the upgrade
-- path explicit.
DO $$
DECLARE
  installed text;
  available text;
BEGIN
  SELECT extversion INTO installed
    FROM pg_extension
   WHERE extname = 'pg_stat_statements';

  SELECT default_version INTO available
    FROM pg_available_extensions
   WHERE name = 'pg_stat_statements';

  IF installed IS DISTINCT FROM available THEN
    RAISE NOTICE 'Upgrading pg_stat_statements: % -> %', installed, available;
    EXECUTE 'ALTER EXTENSION pg_stat_statements UPDATE';
  END IF;
END
$$;
