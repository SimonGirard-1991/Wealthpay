FROM confluentinc/cp-kafka-connect-base:8.1.1

USER root

RUN confluent-hub install --no-prompt debezium/debezium-connector-postgresql:3.1.2

USER appuser
