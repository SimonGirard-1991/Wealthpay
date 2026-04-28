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
  - `model/` — `Account` aggregate (the only aggregate), value objects (`Money`, `AccountId`, `ReservationId`, etc.), sealed `AccountEvent` hierarchy, ID generator **interfaces** (`AccountIdGenerator`, `EventIdGenerator`, `ReservationIdGenerator`). Spring-annotated impls live in `infrastructure/id/`.
  - `command/` — Command records (`OpenAccount`, `CreditAccount`, `DebitAccount`, `ReserveFunds`, `CancelReservation`, `CaptureReservation`, `CloseAccount`).
  - `event/` — Sealed interface `AccountEvent` with 7 concrete event types. Events carry `AccountEventMeta` (eventId, accountId, occurredAt, version).
  - `exception/` — Domain-specific exceptions (invariant violations).
- **`application/`** — Orchestration layer. Ports defined as interfaces (`AccountEventStore`, `AccountEventPublisher`, `AccountSnapshotStore`, `ProcessedTransactionStore`, `ProcessedReservationStore`, `AccountBalanceProjector`, `AccountBalanceReader`). `AccountApplicationService` handles commands transactionally with idempotency (via processed transaction/reservation stores) and snapshot management.
  - `metric/` — application-aware command instrumentation (`@CommandMetric` annotation + aspect). The only place in `application` allowed to use Micrometer's runtime API and AspectJ.
- **`infrastructure/`** — Adapters.
  - `web/` — REST controllers implementing OpenAPI-generated interfaces (`AccountApi`). Explicit DTO-to-domain and domain-to-DTO mappers (one class per mapping direction).
  - `db/repository/` — jOOQ-based repositories implementing application ports. Event serialization/deserialization with JSONB.
  - `consumer/` — Kafka consumer (`AccountOutboxConsumer`) projecting outbox events into the read model.
  - `producer/` — _(reserved)_ Kafka producer adapters when needed; the architecture rules already lock the convention.
  - `id/` — Spring-annotated `Random*IdGenerator` impls of the domain-defined ID-generator ports.
  - `metric/` — adapter-level latency aspect (`@AdapterMetric`).
  - `serialization/` — Jackson `JsonNode` helpers (`AccountEventType`, `MoneyDeserializerUtils`) shared across `db/repository/`, `consumer/`, and the snapshot deserializer (Debezium streams the same JSONB envelope to Kafka, so the wire format is genuinely shared).

### Architecture Rules

Two complementary mechanisms enforce architecture, both in `mvn test`:

- **Spring Modulith** (`account.ArchitectureTests`) — verifies module-to-module dependencies (e.g. `account` may depend on `shared`, not vice-versa).
- **ArchUnit** (`architecture.HexagonalArchitectureTest`) — enforces hexagonal layering _within_ a BC, which Modulith does not cover. 16 rules grouped:
  - **Hexagonal layering** per BC: domain ← application ← infrastructure. Auto-discovers BCs by scanning for top-level packages with a `domain` subpackage; applies the layered rule to each. Fails loudly if zero BCs are detected.
  - **Domain framework-free**: no Spring, jOOQ, Jackson (2.x and 3.x), Kafka, Micrometer, AspectJ, servlets in `..domain..`. Spring-annotated impls of domain-defined ports live in infrastructure.
  - **Application boundary**: no jOOQ, Kafka, web, Jackson, AspectJ, or Micrometer runtime API (`io.micrometer.core..`) in `..application..`. Spring stereotypes (`@Service`, `@Transactional`), Spring DAO exceptions, and declarative observability annotations (`@Observed` from `io.micrometer.observation..`) are allowed — markers interpreted by infrastructure handlers, not coupling to the meter registry.
  - **Infrastructure I/O sibling isolation**: `web ⊥ db ⊥ consumer ⊥ producer`. They communicate only through application ports. Cross-slice helpers live in `..infrastructure.serialization..`, `..infrastructure.metric..`, `..infrastructure.id..`.
  - **Annotation / type locality** (uses `areMetaAnnotatedWith` to also catch composed stereotypes): `@RestController` and `@RestControllerAdvice` only in `..infrastructure.web..`; `@KafkaListener` only in `..infrastructure.consumer..`; `KafkaTemplate` only referenced from `..infrastructure.producer..` or `..config..` (bean wiring).
  - **`@Transactional` location**: only in `..application..` (use-case orchestration) or `..infrastructure.db..` (read-side `readOnly=true` repos).
  - **jOOQ confinement**: both the library (`org.jooq..`: `DSLContext`, `Field`, `JSONB`, etc.) and the generated package (`..jooq..`: tables, records, POJOs) may only be referenced from `..infrastructure.db..` (and the generated package itself). Closes the gap that the layered rule cannot catch — generated jOOQ lives outside the three declared layers, so without this rule any infrastructure subpackage could reach into jOOQ directly.
  - **OpenAPI-generated transport types** (`..api.generated..`) only referenced from `..infrastructure.web..` and the generated package itself. Domain, application, and the non-web infrastructure slices speak in domain types; web maps DTOs in and out via explicit mappers.
  - **No `utils` package at BC root**: auto-applied per discovered BC. Catches the dependency-hub anti-pattern. The `shared` open module (e.g. `shared.utils.MapperUtils`) is exempt by definition since it holds cross-BC helpers and is not a BC.

Carve-outs are explicit and named in the rule bodies:

- `..application.metric..` may import `io.micrometer.core..` and `org.aspectj..` (cross-cutting application instrumentation; outcome lattice is application-aware).
- `..infrastructure.serialization..`, `..infrastructure.metric..`, `..infrastructure.id..` are exempt from I/O sibling isolation (they exist precisely to be shared by the I/O slices).
- `..config..` packages may reference `KafkaTemplate` as a parameter for bean wiring (e.g. `shared.config.KafkaErrorConfig`). Such config doesn't send messages itself.
- `..jooq..` (generated jOOQ) and `..api.generated..` (OpenAPI generated) are exempt from their own confinement rules as _source_ — they legitimately reference each other and `org.jooq..` types internally.

A new BC arrives with full intra-BC layer enforcement on day one — no manual rule registration required. Cross-BC rules (purity, boundary, sibling isolation, annotation locality, jOOQ/OpenAPI confinement) and BC-specific rules (hexagonal layering, no utils package) all apply automatically via package patterns and BC auto-discovery.

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
