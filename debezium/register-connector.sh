#!/bin/bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "wealthpay-outbox-connector",
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

        "schema.include.list": "account",
        "table.include.list": "account.outbox",
        "plugin.name": "pgoutput",
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
