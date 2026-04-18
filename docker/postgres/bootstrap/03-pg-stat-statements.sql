-- Register pg_stat_statements for per-query latency and I/O attribution.
-- Idempotent: safe to re-run on already-initialized volumes.
--
-- The extension ships with the stock postgres:16 image (contrib), so no
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
