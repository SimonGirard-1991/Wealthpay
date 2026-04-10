-- V14: Partition management function and cleanup audit log for account.outbox.
--
-- The function pre-creates future partitions and drops old ones.  It is
-- designed to be called daily — either via pg_cron (recommended in prod)
-- or via the Spring @Scheduled fallback (OutboxCleanupScheduler).

-- 1. Cleanup audit log
CREATE TABLE account.outbox_cleanup_log (
    run_at               timestamptz NOT NULL DEFAULT now(),
    partitions_created   int         NOT NULL,
    partitions_dropped   int         NOT NULL,
    remaining_partitions int         NOT NULL
);

-- 2. Partition management function
CREATE OR REPLACE FUNCTION account.manage_outbox_partitions(retention_days INT DEFAULT 3)
    RETURNS void
    LANGUAGE plpgsql
AS
$$
DECLARE
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

        -- Safe under concurrent execution: the exception handler
        -- catches duplicate_table if another session wins the race.
        BEGIN
            EXECUTE format(
                'CREATE TABLE account.%I PARTITION OF account.outbox FOR VALUES FROM (%L) TO (%L)',
                partition_name,
                partition_date,
                partition_date + 1
            );
            created_count := created_count + 1;
        EXCEPTION WHEN duplicate_table THEN
            NULL; -- partition already created by a concurrent session
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
        -- Parse date from partition name: outbox_YYYY_MM_DD
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
    -- C. Count remaining partitions and log
    -- ---------------------------------------------------------------
    SELECT count(*) INTO remaining_count
    FROM pg_inherits i
    JOIN pg_class parent ON parent.oid = i.inhparent
    JOIN pg_namespace n  ON n.oid = parent.relnamespace
    WHERE n.nspname    = 'account'
      AND parent.relname = 'outbox';

    INSERT INTO account.outbox_cleanup_log (partitions_created, partitions_dropped, remaining_partitions)
    VALUES (created_count, dropped_count, remaining_count);
END;
$$;

-- ---------------------------------------------------------------
-- pg_cron registration (run manually in production):
--
--   SELECT cron.schedule(
--       'outbox-partition-cleanup',
--       '0 3 * * *',
--       $$SELECT account.manage_outbox_partitions(3)$$
--   );
-- ---------------------------------------------------------------
