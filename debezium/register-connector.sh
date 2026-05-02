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
