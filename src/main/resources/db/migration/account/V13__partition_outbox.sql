-- V13: Convert account.outbox to a range-partitioned table (daily partitions on occurred_at).
--
-- Debezium reads the WAL, not the table directly, so the partitioning is
-- transparent to CDC.  The partition key must be part of every unique
-- constraint, so we change UNIQUE(event_id) -> UNIQUE(event_id, occurred_at).

-- 1. Drop the append-only trigger and its backing function
DROP TRIGGER IF EXISTS trg_outbox_no_update ON account.outbox;
DROP FUNCTION IF EXISTS account.outbox_append_only();

-- 2. Rename the existing heap table (its indexes follow the rename automatically)
ALTER TABLE account.outbox RENAME TO outbox_old;

-- 3. Explicitly rename the old index to avoid name collision
ALTER INDEX account.outbox_aggregate_order_idx RENAME TO outbox_old_aggregate_order_idx;

-- 4. Create the partitioned parent table
--    bigserial is not supported on partitioned tables, use GENERATED ALWAYS AS IDENTITY
CREATE TABLE account.outbox (
    outbox_id         bigint       GENERATED ALWAYS AS IDENTITY,
    event_id          uuid         NOT NULL,
    aggregate_type    text         NOT NULL,
    aggregate_id      uuid         NOT NULL,
    aggregate_version bigint       NOT NULL,
    event_type        text         NOT NULL,
    occurred_at       timestamptz  NOT NULL,
    payload           jsonb        NOT NULL,
    UNIQUE (event_id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- 5. Re-create the aggregate ordering index on the parent
CREATE INDEX outbox_aggregate_order_idx
    ON account.outbox (aggregate_id, aggregate_version);

-- 6. Create initial partitions using the outbox_YYYY_MM_DD naming convention
--    (same convention and safety window used by manage_outbox_partitions in V14)
DO $$
DECLARE
    i            INT;
    part_date    DATE;
    part_name    TEXT;
BEGIN
    FOR i IN 0..7 LOOP
        part_date := CURRENT_DATE + i;
        part_name := 'outbox_' || to_char(part_date, 'YYYY_MM_DD');
        EXECUTE format(
            'CREATE TABLE account.%I PARTITION OF account.outbox FOR VALUES FROM (%L) TO (%L)',
            part_name,
            part_date,
            part_date + 1
        );
    END LOOP;
END;
$$;

-- 7. Drop the old table — do NOT backfill.
--
--    All historical outbox rows have already been published to Kafka through
--    the WAL (Debezium CDC).  Re-inserting them into the new partitioned table
--    would generate fresh WAL entries, causing Debezium to re-publish them as
--    duplicate events.  The event store is the source of truth; the outbox is
--    an ephemeral CDC relay, not an archive.
DROP TABLE account.outbox_old CASCADE;

-- 8. Re-create the append-only trigger on the partitioned parent.
--    Triggers defined on a partitioned parent are inherited by all partitions.
CREATE OR REPLACE FUNCTION account.outbox_append_only()
    RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    RAISE EXCEPTION 'account.outbox is append-only. % is not allowed.', TG_OP;
END;
$$;

CREATE TRIGGER trg_outbox_no_update
    BEFORE UPDATE
    ON account.outbox
    FOR EACH ROW
EXECUTE FUNCTION account.outbox_append_only();
