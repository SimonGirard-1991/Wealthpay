#!/bin/bash
# Targets the post-cutover production-shaped slot (debezium_pg18, snapshot.mode=never).
#
# DELETE pre-flight handles BOTH connector names: the new pg18-suffixed one
# (drop-and-recreate idempotency on the post-cutover stack) AND the legacy
# pre-cutover one (clears Phase 3's residual entry in connect-configs that
# Path C left behind on stacks where Phase 6 dropped+recreated rather than
# mutating in place). Without the legacy DELETE, a developer running this
# script on a stack that still has the pre-cutover connector registered ends
# up with two connectors competing for the outbox table.
#
# publication.autocreate.mode=disabled makes the connector a CONSUMER of the
# publication and never an author of it. The publication is owned solely by
# db/migration/account/V18__narrow_dbz_publication_to_outbox.sql, which creates it
# as FOR TABLE account.outbox and fails the migration if it is not exactly that.
# This matters because a FOR ALL TABLES publication decodes every table in the
# database into this JVM, where connector-side filters are all that stop personal
# data reaching a topic -- see docs/adr/009-pii-retention-and-erasure.md, D8.
#
# ORDERING CONSEQUENCE, and it changes local bring-up: "disabled" means the
# connector will not start until the publication exists, so MIGRATIONS MUST RUN
# BEFORE THIS SCRIPT. Start the app once (or run Flyway) and then register. The
# failure is loud -- the task errors saying the publication is missing -- rather
# than a connector that silently creates the wrong thing.
#
# Register BEFORE sending any traffic, too. snapshot.mode=never starts streaming
# from the current LSN, so any outbox row written between the app accepting
# requests and this script running is never published, and there is no tool to
# re-derive it. On a fresh stack that window is the gap between `mvn
# spring-boot:run` and this script.
#
# Rejected: "filtered", the obvious alternative, verified against the pinned
# debezium-connector-postgresql 3.1.2 rather than assumed:
#   * its CREATE PUBLICATION does NOT set publish_via_partition_root, whose
#     PostgreSQL default is false. V13 range-partitioned account.outbox, so a
#     publication created that way attributes changes to the child partitions and
#     table.include.list stops matching. Letting the connector create the
#     publication therefore creates a BROKEN one, repaired only at the next
#     migration run;
#   * it does not merely create. On an existing, non-FOR-ALL-TABLES publication it
#     issues `ALTER PUBLICATION ... SET TABLE`, so membership would be rewritten
#     behind the migration that is supposed to own it -- and it throws outright if
#     the existing publication is FOR ALL TABLES.
# "all_tables", the connector default, is what produced the leak in the first
# place; stating the mode explicitly is what keeps that default from returning.
#
# publication.name is stated explicitly rather than left to its default so that
# the connector and the migration are visibly talking about the same object.
#
# heartbeat.interval.ms is a direct consequence of that narrowing, not tuning.
# Debezium only acknowledges an LSN it has processed. While the publication was
# FOR ALL TABLES the connector received every table's changes and discarded the
# unwanted ones, so it always had a position to confirm; now only account.outbox
# traffic reaches it. An idle outbox on a database that is otherwise busy — event
# store writes, the customer schema, autovacuum — is exactly the shape that leaves
# confirmed_flush_lsn stationary while WAL accumulates, which is the most common
# way a Debezium deployment fills a production disk. Heartbeats give the connector
# something to acknowledge on a fixed interval. max_slot_wal_keep_size in
# docker-compose.local.yml is the backstop for when it is not enough.
#
# heartbeat.action.query is deliberately NOT set. Debezium's documentation lists
# three distinct causes of WAL growth, and this stack is the second: many updates
# in the captured database, few in the captured tables, for which
# heartbeat.interval.ms alone is the prescribed remedy. action.query is scoped to
# the third — a low-traffic database sharing a host with a high-traffic one, where
# replication slots are per-database and the connector is never invoked at all.
# One database here, so the heartbeat always has a moving LSN to report.
#
# IF SLOT LAG APPEARS ANYWAY and you reach for action.query, it is a four-part
# change and not a config line. Deciding it at 03:00 with pg_wal filling is the
# situation this note exists to prevent:
#   1. The heartbeat table must be ADDED TO THE PUBLICATION. Debezium cannot
#      process an event it never receives, so an unpublished heartbeat table
#      leaves the LSN exactly where it was — the change would look applied and do
#      nothing.
#   2. With autocreate disabled the connector will not add it for you, so V18 has
#      to create the publication over both tables. Do not switch the mode to
#      "filtered" to get that for free: see the rejected-alternative note above.
#   3. That widens the publication, so V18's post-condition (which requires
#      exactly account.outbox) must be widened with it, deliberately.
#   4. So must PerBoundedContextMigrationTest's assertion that logical replication
#      carries account.outbox and nothing else.
# Both assertions are data-protection controls. Widening them is a decision to
# record, not an obstacle to route around.
#
# snapshot.mode=never assumes the slot already has a confirmed_flush_lsn from
# the post-cutover cluster, so streaming starts from "now" without an initial
# table snapshot. **Fresh-clone bring-up note:** a brand-new developer
# bringing the stack up from scratch will NOT get an initial backfill of
# account_balance_view from the existing outbox rows — they should run this
# script once with snapshot.mode patched to "initial" (or use the
# `register-connector.sh --bootstrap` flow if it lands in a future PR), then
# revert to "never" for subsequent re-registrations.
curl -s -o /dev/null -X DELETE http://localhost:8083/connectors/wealthpay-outbox-connector-pg18 2>/dev/null || true
curl -s -o /dev/null -X DELETE http://localhost:8083/connectors/wealthpay-outbox-connector 2>/dev/null || true
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "wealthpay-outbox-connector-pg18",
    "config": {
        "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
        "tasks.max": "1",
        "database.hostname": "postgres",
        "database.port": "5432",
        "database.user": "user",
        "database.password": "password",
        "database.dbname": "wealthpay",
        "topic.prefix": "wealthpay",

        "topic.creation.enable": "true",
        "topic.creation.default.replication.factor": "3",
        "topic.creation.default.partitions": "3",
        "poll.interval.ms": "100",

        "schema.include.list": "account",
        "table.include.list": "account.outbox",
        "plugin.name": "pgoutput",

        "publication.name": "dbz_publication",
        "publication.autocreate.mode": "disabled",
        "heartbeat.interval.ms": "10000",
        "slot.name": "debezium_pg18",
        "snapshot.mode": "never",
        "key.converter": "org.apache.kafka.connect.storage.StringConverter",
        "transforms": "outbox",
        "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
        "transforms.outbox.route.by.field": "aggregate_type",
        "transforms.outbox.route.topic.replacement": "wealthpay.${routedByValue}",
        "transforms.outbox.table.field.event.key": "aggregate_id",
        "transforms.outbox.table.field.event.payload": "payload",
        "transforms.outbox.table.field.event.id": "event_id",
        "transforms.outbox.table.fields.additional.placement": "event_type:header:eventType,aggregate_version:header:aggregateVersion,occurred_at:header:occurredAt"
    }
}'
