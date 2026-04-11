-- 1. Register pg_cron job for daily partition lifecycle management.
--    Guarded: environments without pg_cron (tests, CI) silently skip.
--    Retention uses the function's DEFAULT parameter (3 days) as single source of truth.
--    To change retention, update the DEFAULT in manage_outbox_partitions() (V16).
DO $do$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_cron') THEN
        PERFORM cron.schedule(
            'outbox-partition-cleanup',
            '0 3 * * *',
            $$SELECT account.manage_outbox_partitions()$$
        );
    END IF;
END;
$do$;

-- 2. Indexes for the observer's top-N queries on outbox_cleanup_log.
--    The table is small today (~1 row/day), but unindexed ORDER BY ... LIMIT 1
--    degrades silently as rows accumulate over years × N polling replicas.
CREATE INDEX idx_outbox_cleanup_log_run_at_desc
    ON account.outbox_cleanup_log (run_at DESC);

CREATE INDEX idx_outbox_cleanup_log_completed_at_success
    ON account.outbox_cleanup_log (completed_at DESC)
    WHERE status = 'success';
