-- V16: Evolve outbox_cleanup_log to capture failures and timing.
--
-- The function now records both successes and failures (with error messages)
-- so that the Spring observer can distinguish "cleanup failed" from
-- "cleanup never ran".  Timing uses clock_timestamp() (wall-clock) rather
-- than now() (transaction-start) for accurate duration measurement.

-- 1. Narrow the Debezium publication to only the outbox table.
--    V15 used FOR ALL TABLES, which also publishes outbox_cleanup_log.
--    That table is an internal audit log with no PK — UPDATEs are blocked
--    because PostgreSQL requires a replica identity for published updates.
--    Only account.outbox belongs in the CDC pipeline.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'dbz_publication') THEN
        DROP PUBLICATION dbz_publication;
        CREATE PUBLICATION dbz_publication FOR TABLE account.outbox
            WITH (publish_via_partition_root = true);
    END IF;
END;
$$;

-- 2. Add observability columns without clobbering historical run timestamps
ALTER TABLE account.outbox_cleanup_log
    ADD COLUMN status        text        NOT NULL DEFAULT 'success'
                              CHECK (status IN ('success', 'failure')),
    ADD COLUMN started_at    timestamptz,
    ADD COLUMN completed_at  timestamptz,
    ADD COLUMN error_message text;

UPDATE account.outbox_cleanup_log
SET started_at = run_at,
    completed_at = run_at
WHERE started_at IS NULL
   OR completed_at IS NULL;

ALTER TABLE account.outbox_cleanup_log
    ALTER COLUMN started_at SET NOT NULL,
    ALTER COLUMN started_at SET DEFAULT now(),
    ALTER COLUMN completed_at SET NOT NULL,
    ALTER COLUMN completed_at SET DEFAULT now();

-- 3. Restructure function with top-level EXCEPTION block
CREATE OR REPLACE FUNCTION account.manage_outbox_partitions(retention_days INT DEFAULT 3)
    RETURNS void
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_started_at     timestamptz := clock_timestamp();
    i                INT;
    partition_date   DATE;
    partition_name   TEXT;
    created_count    INT := 0;
    dropped_count    INT := 0;
    remaining_count  INT;
    rec              RECORD;
    partition_dt     DATE;
BEGIN
    -- ---------------------------------------------------------------
    -- A. Pre-create partitions for today + next 7 days (idempotent)
    -- ---------------------------------------------------------------
    FOR i IN 0..7 LOOP
        partition_date := CURRENT_DATE + i;
        partition_name := 'outbox_' || to_char(partition_date, 'YYYY_MM_DD');

        BEGIN
            EXECUTE format(
                'CREATE TABLE account.%I PARTITION OF account.outbox FOR VALUES FROM (%L) TO (%L)',
                partition_name,
                partition_date,
                partition_date + 1
            );
            created_count := created_count + 1;
        EXCEPTION WHEN duplicate_table THEN
            NULL;
        END;
    END LOOP;

    -- ---------------------------------------------------------------
    -- B. Drop partitions older than retention_days
    -- ---------------------------------------------------------------
    FOR rec IN
        SELECT c.relname AS child_name
        FROM pg_inherits i
        JOIN pg_class c      ON c.oid = i.inhrelid
        JOIN pg_class parent ON parent.oid = i.inhparent
        JOIN pg_namespace n  ON n.oid = parent.relnamespace
        WHERE n.nspname   = 'account'
          AND parent.relname = 'outbox'
          AND c.relname ~ '^outbox_\d{4}_\d{2}_\d{2}$'
    LOOP
        partition_dt := to_date(
            substring(rec.child_name FROM 'outbox_(\d{4}_\d{2}_\d{2})'),
            'YYYY_MM_DD'
        );

        IF partition_dt < CURRENT_DATE - retention_days THEN
            EXECUTE format('DROP TABLE IF EXISTS account.%I', rec.child_name);
            dropped_count := dropped_count + 1;
        END IF;
    END LOOP;

    -- ---------------------------------------------------------------
    -- C. Count remaining partitions
    -- ---------------------------------------------------------------
    SELECT count(*) INTO remaining_count
    FROM pg_inherits i
    JOIN pg_class parent ON parent.oid = i.inhparent
    JOIN pg_namespace n  ON n.oid = parent.relnamespace
    WHERE n.nspname    = 'account'
      AND parent.relname = 'outbox';

    -- ---------------------------------------------------------------
    -- D. Log success
    -- ---------------------------------------------------------------
    INSERT INTO account.outbox_cleanup_log
        (partitions_created, partitions_dropped, remaining_partitions,
         status, started_at, completed_at)
    VALUES
        (created_count, dropped_count, remaining_count,
         'success', v_started_at, clock_timestamp());

EXCEPTION WHEN OTHERS THEN
    -- The partition work above is rolled back (PL/pgSQL subtransaction).
    -- We do NOT re-raise: the failure row IS the signal.  If we re-raised,
    -- pg_cron's outer transaction would abort and roll back this INSERT too,
    -- defeating the entire purpose of failure tracking.
    INSERT INTO account.outbox_cleanup_log
        (partitions_created, partitions_dropped, remaining_partitions,
         status, started_at, completed_at, error_message)
    VALUES
        (0, 0, 0,
         'failure', v_started_at, clock_timestamp(), SQLERRM);
END;
$$;
