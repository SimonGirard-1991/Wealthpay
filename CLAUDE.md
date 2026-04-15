# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Start local infrastructure (Postgres, Kafka 3-node KRaft cluster, Schema Registry, Kafka Connect, Prometheus, Grafana, Alertmanager)
./scripts/infra.sh

# Register Debezium CDC connector (after Kafka Connect is healthy)
./debezium/register-connector.sh

# Run the application (also applies Flyway migrations)
mvn spring-boot:run

# Build (no running database required — jOOQ sources are committed)
mvn clean install

# Regenerate jOOQ classes after schema changes (requires running Postgres)
mvn -Pjooq-codegen-local clean generate-sources

# Format code (Google Java Format via Spotless)
mvn spotless:apply

# Check formatting
mvn spotless:check

# Mutation testing (PITest, 80% mutation score threshold)
mvn pitest:mutationCoverage

# Run a single test class
mvn test -Dtest=AccountCreditTest

# Run a single test method
mvn test -Dtest=AccountCreditTest#creditAccount_emits_FundsCredited_event_and_updates_account_balance

# Gatling load tests
mvn -Pgatling gatling:test
```

## Architecture

This is a **modular monolith** implementing a banking account domain using **DDD**, **Event Sourcing**, **CQRS**, and **Hexagonal Architecture** with Spring Boot 4.0+ and Spring Modulith.

### Module Structure

Two Spring Modulith modules under `org.girardsimon.wealthpay`:

- **`account`** — CLOSED module, depends only on `shared`. Contains the entire account domain (aggregate, events, commands), application services, and infrastructure adapters.
- **`shared`** — OPEN module, no dependencies. Cross-cutting concerns: Clock config, JSON serialization, global exception handling, utility mappers.

Module boundaries are enforced by `ArchitectureTests` via `ApplicationModules.of(...).verify()`.

### Hexagonal Layers (within `account`)

- **`domain/`** — Pure domain logic, zero framework dependencies.
  - `model/` — `Account` aggregate (the only aggregate), value objects (`Money`, `AccountId`, `ReservationId`, etc.), sealed `AccountEvent` hierarchy, ID generators.
  - `command/` — Command records (`OpenAccount`, `CreditAccount`, `DebitAccount`, `ReserveFunds`, `CancelReservation`, `CaptureReservation`, `CloseAccount`).
  - `event/` — Sealed interface `AccountEvent` with 7 concrete event types. Events carry `AccountEventMeta` (eventId, accountId, occurredAt, version).
  - `exception/` — Domain-specific exceptions (invariant violations).
- **`application/`** — Orchestration layer. Ports defined as interfaces (`AccountEventStore`, `AccountEventPublisher`, `AccountSnapshotStore`, `ProcessedTransactionStore`, `ProcessedReservationStore`, `AccountBalanceProjector`, `AccountBalanceReader`). `AccountApplicationService` handles commands transactionally with idempotency (via processed transaction/reservation stores) and snapshot management.
- **`infrastructure/`** — Adapters.
  - `web/` — REST controllers implementing OpenAPI-generated interfaces (`AccountApi`). Explicit DTO-to-domain and domain-to-DTO mappers (one class per mapping direction).
  - `db/repository/` — jOOQ-based repositories implementing application ports. Event serialization/deserialization with JSONB.
  - `consumer/` — Kafka consumer (`AccountOutboxConsumer`) projecting outbox events into the read model.

### Event Sourcing Flow

1. Command enters via REST controller → mapped to domain command
2. `AccountApplicationService` loads the `Account` aggregate via `AccountLoader` (snapshot + replay events after snapshot version)
3. `Account.handle(command)` validates invariants and returns new events (pure, no side effects)
4. Events are appended to `event_store` table with optimistic concurrency (expected version)
5. Events are simultaneously written to `outbox` table (same transaction)
6. Debezium captures outbox changes via PostgreSQL WAL (CDC) and routes to Kafka topic `wealthpay.AccountEvent`
7. `AccountOutboxConsumer` projects events into `account_balance_view` read model

### Key Design Decisions

- **Aggregate state is rebuilt via `apply()` method** using pattern matching on the sealed `AccountEvent` hierarchy. `rehydrate()` replays from event history; `rehydrateFromSnapshot()` starts from a snapshot.
- **Snapshots** are taken every N events (configurable via `ACCOUNT_SNAPSHOT_THRESHOLD`, default 100). Snapshot failures are logged but never block the critical path.
- **Idempotency** is enforced at the application layer via `processed_transactions` and `processed_reservations` tables with fingerprint tracking.
- **OpenAPI-first**: REST DTOs and controller interfaces are generated from `src/main/resources/openapi/` YAML specs. Do not edit generated code under `target/generated-sources/openapi/`.
- **jOOQ classes** are generated under `src/main/generated-jooq/` and committed to git (OSS jOOQ requires a live DB for codegen). Do not edit these manually.
- **Outbox table** is range-partitioned by day with pg_cron-managed partition creation/cleanup.

## Code Style

- **Google Java Format** enforced by Spotless. 2-space indent, 100-char line length.
- Pre-commit hook runs `spotless:check` — configure with `git config core.hooksPath .githooks`.
- Spotless uses `ratchetFrom: origin/main` — only changed files are checked.
- Flyway migrations (`src/main/resources/db/migration/`) are immutable and excluded from formatting.
- Generated jOOQ classes (`src/main/generated-jooq/`) are excluded from formatting.

## Testing Conventions

- **Domain tests** — Pure unit tests per command (`AccountCreditTest`, `AccountDebitTest`, etc.) using `TestEventIdGenerator` for deterministic IDs.
- **Application service tests** — Mocked ports (event store, publishers, etc.).
- **Web layer tests** — `@WebMvcTest` with mocked application services.
- **Repository tests** — Extend `AbstractContainerTest` which provides a Testcontainers PostgreSQL instance with Flyway migrations.
- **Kafka consumer tests** — `@EmbeddedKafka`.
- **Mapper tests** — Unit tests for every DTO-to-domain and domain-to-DTO mapper.

## Environment Variables

Defined in `.env` (loaded by docker-compose and IDE run configs):
- `DB_URL`, `DB_USER`, `DB_PASSWORD` — PostgreSQL connection
- `KAFKA_BOOTSTRAP_SERVERS` — Kafka brokers (3-node cluster: ports 9092, 9093, 9094)
- `SCHEMA_REGISTRY_URL` — Confluent Schema Registry
- `ACCOUNT_SNAPSHOT_THRESHOLD` — Snapshot frequency (default 100)
- `OUTBOX_CLEANUP_RETENTION_DAYS` — Spring fallback scheduler retention (default 3). Production retention is set in the pg_cron function's DEFAULT parameter via Flyway migration.
