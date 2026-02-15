# ADR-004: Event Contract

## Status

Accepted

## Context

Domain events are published to Kafka via Debezium CDC (see ADR-003).
The Debezium EventRouter extracts the `payload` column (JSONB) from the
outbox table and produces it as the Kafka message value.

We need to define the contract of the Kafka message: how business data,
event metadata, and event type information are structured and serialized.

Key constraints:

- The outbox stores event payloads as JSONB (PostgreSQL native type).
- Debezium EventRouter extracts the JSONB payload and emits it as a JSON string.
- Kafka Connect uses the Avro converter, so the value is serialized as an
  Avro `string` type in the Schema Registry — no structural schema.
- Currently, a single internal consumer (account projection).

## Decision

### Message structure

| Part    | Content                                             | Example                          |
|---------|-----------------------------------------------------|----------------------------------|
| Key     | `aggregate_id` (UUID string)                        | `"550e8400-..."` (partition key) |
| Value   | JSON string, schema-less, event-type-specific       | `{"currency":"USD","amount":50}` |
| Headers | `id`, `eventType`, `aggregateVersion`, `occurredAt` | routing and idempotency metadata |

The Kafka key is kept as a plain string for stable partitioning and operational simplicity; Avro is used only as the
value container.

### Separation of concerns

- **Value** carries only business data specific to the event type.
  No metadata, no envelope wrapper.
- **Headers** carry routing and processing metadata (`eventType` for
  deserialization dispatch, `aggregateVersion` for ordering/idempotency,
  `occurredAt` for temporal context, `id` for deduplication).
- **Key** is the aggregate identifier, ensuring per-aggregate Kafka
  partition affinity.

This separation is enforced by the Debezium EventRouter configuration
(`table.fields.additional.placement`).

### Schema-less JSON payload

The event payload is not validated by the Schema Registry.
The Avro schema registered is `{"type": "string"}` — it wraps the JSON
string but does not describe its structure.

Structural validation is performed at consumption time by the
`AccountEventDeserializer`, which parses the JSON and validates
required fields per event type.

## Consequences

### Positive

- Natural fit with the JSONB outbox: no serialization transformation
  between the event store and Kafka.
- Schema evolution is implicit: adding a field to the JSON payload is
  backwards-compatible for consumers that ignore unknown fields.
- Easy to reason about and debug (human-readable JSON in Kafka).

### Negative

- No produce-time validation: a malformed payload will only be caught
  at consumption time.
- The Schema Registry provides no structural compatibility checks
  (BACKWARD/FORWARD) on the event content.
- Consumer must handle all validation and type dispatching manually
  (switch on `eventType` header).

## Alternatives Considered

### Typed Avro records (.avsc per event type)

Would provide:

- Produce-time schema validation via Schema Registry.
- Cross-language code generation.
- Formal compatibility guarantees (BACKWARD, FORWARD).

Not adopted because:

- Single internal consumer: the contract is enforced in code, not across teams.
- Would require either Avro serialization in the outbox (coupling domain
  to Avro) or a Kafka Streams / SMT transformation layer.
- Added complexity disproportionate to current needs.
- May be reconsidered if events become a public contract consumed by
  external services.

### Full envelope in value (metadata and payload in a single JSON object)

Not adopted because:

- Duplicates information already available in Kafka headers and keys.
- Increases message size.
- Debezium EventRouter natively supports header placement, making this
  unnecessary.
