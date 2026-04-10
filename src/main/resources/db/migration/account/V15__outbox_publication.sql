-- V15: Enable publish_via_partition_root on the Debezium-managed publication.
--
-- V13 converted account.outbox into a range-partitioned table.  Inserts now
-- physically land in child partitions (outbox_YYYY_MM_DD).  By default,
-- PostgreSQL publications emit changes under the child partition identity,
-- which can break Debezium's table.include.list filter (set to account.outbox).
--
-- publish_via_partition_root = true makes all child-partition changes appear
-- as if they originated from the parent table, keeping the CDC pipeline
-- transparent to partitioning.
--
-- This migration handles both workflow orderings:
--   A) Connector registered before Flyway → dbz_publication already exists → ALTER it.
--   B) Flyway runs before connector       → no publication yet → create it with correct settings.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'dbz_publication') THEN
        ALTER PUBLICATION dbz_publication SET (publish_via_partition_root = true);
    ELSE
        EXECUTE 'CREATE PUBLICATION dbz_publication FOR ALL TABLES WITH (publish_via_partition_root = true)';
    END IF;
END;
$$;
