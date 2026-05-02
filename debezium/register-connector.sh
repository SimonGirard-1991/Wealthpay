#!/bin/bash
curl -s -o /dev/null -X DELETE http://localhost:8083/connectors/wealthpay-outbox-connector-pg18 2>/dev/null || true
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
