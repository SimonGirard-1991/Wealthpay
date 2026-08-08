-- V18: Narrow dbz_publication so the replication stream carries account.outbox and nothing else.
--
-- The publication is FOR ALL TABLES today, and two independent paths put it there:
--   * the Debezium connector's publication.autocreate.mode default, all_tables;
--   * V15's ELSE branch, which creates it FOR ALL TABLES when it does not already exist.
-- Whichever wins the race, the result is the same, which is why narrowing one path alone is not
-- the invariant.
--
-- FOR ALL TABLES means every table in this database is decoded into the logical replication stream
-- and shipped to the Kafka Connect JVM, where two connector-side filters (table.include.list and
-- schema.include.list, both living in an unversioned shell script) are all that keep it off a
-- topic. That is a filter, not a boundary. From the moment a schema holding personal data exists,
-- what crosses that filter is personal data, so the boundary has to be in the database.
-- See docs/adr/009-pii-retention-and-erasure.md, D1 and D8.
--
-- The swap has to be a DROP followed by a CREATE: ALTER PUBLICATION ... DROP TABLE is rejected on a
-- FOR ALL TABLES publication ("Tables cannot be added to or dropped from FOR ALL TABLES
-- publications"). Both statements are transactional, so a streaming walsender observes one
-- definition or the other and never an empty window, and an existing replication slot is unaffected.
--
-- V15 is immutable and is not edited. This migration supersedes its ELSE branch while preserving
-- the setting V15 exists for: publish_via_partition_root, which account.outbox needs because V13
-- range-partitioned it and Debezium filters on the parent name.

-- CONVERGES on the wanted state rather than only widening-to-narrow, because there is more than one
-- wrong state and only one of them is "FOR ALL TABLES". The test is therefore "is this EXACTLY the
-- publication this migration creates", not "does it contain account.outbox":
--
--   * FOR ALL TABLES                  -- the state this migration exists for;
--   * narrowed to the wrong tables    -- a different or additional set is published;
--   * narrowed and EMPTY              -- needs no mistake at all. PostgreSQL silently removes a table
--     from a publication when the table is dropped, so any path that recreates account.outbox (a
--     re-partitioning migration, a restore, a Flyway clean) leaves the publication publishing nothing;
--   * membership right, OPERATIONS DISABLED -- `ALTER PUBLICATION dbz_publication SET (publish =
--     'update, delete')` clears pubinsert while account.outbox stays listed. The outbox emits nothing
--     but INSERTs, so that publication is inert and a membership-only check passes it;
--   * membership right, ROW FILTER or COLUMN LIST attached -- `FOR TABLE account.outbox WHERE
--     (aggregate_type = 'Nope')` is a working publication that carries no outbox row, and it too is
--     invisible to a membership check;
--   * publish_via_partition_root off  -- V13 range-partitioned account.outbox, so Debezium's
--     table.include.list only matches while changes are attributed to the parent. This is also what
--     Debezium's own autocreate would produce, since its CREATE PUBLICATION omits the option.
--
-- All verified against PostgreSQL 18. Every one is repaired by the same DROP and CREATE, so this is a
-- single "not canonical" test rather than a list of special cases.
--
-- The predicate is defined ONCE, as a permanent function, because it is needed in at least three
-- places -- to decide whether to repair, to assert the repair worked, and by the standing test that
-- checks the invariant still holds long after this migration ran. Six clauses copied into each of those
-- is how one of them ends up subtly weaker than the others, which is the defect this whole migration
-- exists to correct. A future monitoring query is the fourth caller and the reason it is not pg_temp.
--
-- MEMBERSHIP LIVES IN THREE CATALOGS, and reading one is how a leak gets accepted:
--   * pg_publication          -- puballtables, pubviaroot, and the four operation flags;
--   * pg_publication_rel      -- per-table membership, and the ONLY place a row filter (prqual) or a
--                                column list (prattrs) appears. It also names the relation actually
--                                published rather than its expansion, so publishing a partition child
--                                instead of the parent is caught here too;
--   * pg_publication_namespace -- FOR TABLES IN SCHEMA (PostgreSQL 15+). This is the one that matters
--                                most in practice: `ALTER PUBLICATION dbz_publication ADD TABLES IN
--                                SCHEMA customer` is a single statement, and it is exactly what someone
--                                adding customer CDC would reach for -- so the most likely deliberate
--                                route to the ADR-009 D1/D8 leak is this clause, not FOR ALL TABLES.
-- pg_publication_tables shows only the expansion, so it hides disabled operations and filters entirely.
--
-- Not covered, deliberately: PostgreSQL 18's publish_generated_columns (pubgencols). account.outbox has
-- no generated columns, so it changes nothing today; a future generated column on the outbox would drop
-- out of the stream under the default and would need this predicate extended.
CREATE OR REPLACE FUNCTION account.dbz_publication_is_canonical()
    RETURNS boolean
    LANGUAGE sql
    STABLE
AS
$$
SELECT EXISTS (SELECT 1
                 FROM pg_publication p
                WHERE p.pubname = 'dbz_publication'
                  AND NOT p.puballtables
                  AND p.pubviaroot
                  AND p.pubinsert
                  AND p.pubupdate
                  AND p.pubdelete
                  AND p.pubtruncate
                  AND NOT EXISTS (SELECT 1
                                    FROM pg_publication_namespace pn
                                   WHERE pn.pnpubid = p.oid)
                  AND (SELECT count(*) FROM pg_publication_rel r WHERE r.prpubid = p.oid) = 1
                  AND EXISTS (SELECT 1
                                FROM pg_publication_rel r
                                         JOIN pg_class c ON c.oid = r.prrelid
                                         JOIN pg_namespace n ON n.oid = c.relnamespace
                               WHERE r.prpubid = p.oid
                                 AND n.nspname = 'account'
                                 AND c.relname = 'outbox'
                                 AND r.prqual IS NULL
                                 AND r.prattrs IS NULL));
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'dbz_publication')
        AND NOT account.dbz_publication_is_canonical() THEN
        DROP PUBLICATION dbz_publication;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'dbz_publication') THEN
        CREATE PUBLICATION dbz_publication
            FOR TABLE account.outbox
            WITH (publish_via_partition_root = true);
    END IF;
END;
$$;

-- Assert the outcome rather than the statements above, and fail the migration if it does not hold.
-- Silently skipping is the one behaviour this migration must not have: the whole point is to stop
-- relying on a control that fails open.
--
-- The post-condition is a BICONDITIONAL, in two halves, because either half alone fails open in one
-- direction:
--   * nothing MORE than account.outbox is published anywhere -- the data-protection half. This one
--     reads pg_publication_tables deliberately, since it asks what any publication would actually
--     decode, expansion and all, across every publication in the database;
--   * dbz_publication is a WORKING publication for account.outbox -- the availability half. "Nothing
--     carries anything else" is satisfied by a publication that carries nothing, which is a dead CDC
--     pipeline reported as a successful migration, and the outbox depth gauge fails into the healthy
--     band.
--
-- Reasons this can legitimately fail, and what to do:
--   * The migration role does not own dbz_publication, so the DROP above could not run. On a managed
--     instance a plain schema owner has neither superuser rights nor publication ownership (WP-22).
--     Provision the publication out of band as FOR TABLE account.outbox, then re-run.
--   * A second publication was added for another purpose. That is a deliberate act and needs a
--     deliberate decision about what it is allowed to carry -- so it is not repaired, only reported.
DO $$
DECLARE
    unexpected TEXT;
BEGIN
    SELECT string_agg(format('%s.%s (in %s)', schemaname, tablename, pubname),
                      ', ' ORDER BY pubname, schemaname, tablename)
      INTO unexpected
      FROM pg_publication_tables
     WHERE NOT (schemaname = 'account' AND tablename = 'outbox');

    IF unexpected IS NOT NULL THEN
        RAISE EXCEPTION
            'logical replication would carry tables other than account.outbox: %.',
            unexpected
        USING HINT =
            'A publication is FOR ALL TABLES, or carries a FOR TABLES IN SCHEMA clause, or has had '
            'publish_via_partition_root turned off. Narrow every publication to the tables it is meant '
            'to carry before any schema holding personal data is created (ADR-009 D1/D8).';
    END IF;

    IF NOT account.dbz_publication_is_canonical() THEN
        RAISE EXCEPTION
            'dbz_publication is not a working publication for account.outbox, so change data capture '
            'is inert.'
        USING HINT =
            'It must publish exactly account.outbox, with publish_via_partition_root on, all four '
            'operations enabled, and no row filter or column list. pubinsert and the row filter are '
            'the ones to check first: the outbox emits only INSERTs, so either can leave the table '
            'listed while nothing is carried.';
    END IF;
END;
$$;
