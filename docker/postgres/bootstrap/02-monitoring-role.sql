-- Read-only role used by postgres_exporter for Prometheus scraping.
-- Idempotent: safe to re-run on already-initialized volumes.
--
-- pg_monitor is a built-in role (PG10+) that transitively includes
-- pg_read_all_stats and pg_read_all_settings. It grants visibility on
-- every pg_stat_* view without any superuser privileges. This is the
-- least-privilege baseline recommended by the PostgreSQL docs for
-- external monitoring.
--
-- LOCAL-ONLY CREDENTIALS: the literal password below is for the local
-- docker-compose stack only. When lifting this wiring to a non-local
-- environment, route through Docker/K8s secrets and use the exporter's
-- DATA_SOURCE_PASS_FILE / DATA_SOURCE_URI_FILE variants — do NOT copy
-- this file verbatim.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'monitoring') THEN
    CREATE ROLE monitoring LOGIN PASSWORD 'monitoring';
  END IF;
END
$$;

GRANT pg_monitor TO monitoring;
GRANT CONNECT ON DATABASE wealthpay TO monitoring;
