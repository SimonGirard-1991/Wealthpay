# WealthPay — Modular Account Domain (DDD + Event Sourcing)

WealthPay is a personal side project focused on designing and implementing a clean and extensible **banking-grade
account domain**, using modern architectural practices such as **Domain-Driven Design**, **Event Sourcing**, **CQRS**,
and **Modular Monolith** principles with Spring Modulith.

The goal is to build a fully consistent and testable domain for account operations (open, credit, debit, reserve funds,
cancel reservation, close account) using techniques common in real financial systems.

---

## 📋 Prerequisites

| Tool   | Version | Notes                                 |
|--------|---------|---------------------------------------|
| Java   | 25+     | Required for flexible `main()`        |
| Maven  | 3.9+    |                                       |
| Docker | 24+     | For PostgreSQL, Kafka, Debezium, etc. |
| Git    | 2.9+    | For custom hooks path                 |

---

## 🏗 Architecture

### ✔ Domain-Driven Design (DDD)

- Explicit domain model (aggregate, value objects, domain invariants)
- Dedicated domain exceptions with meaningful semantics
- Commands and events as the main input/output of the aggregate

### ✔ Event Sourcing

- Every state change is captured as an immutable domain event
- Aggregate state is rebuilt through event replay (`rehydrate`)
- Event store backed by **PostgreSQL** (`event_store` table + JSONB payloads)
- Snapshot threshold configurable via `ACCOUNT_SNAPSHOT_THRESHOLD` env var (default to 100)

### ✔ CQRS

- Commands mutate state via events
- Queries rely on a read projection kept eventually consistent via Kafka

### ✔ Transactional Outbox + CDC

- Events are written to an `outbox` table in the same transaction as the event store
- Debezium captures outbox changes from the PostgreSQL WAL (Change Data Capture)
- Events are routed to Kafka topics via the Debezium Outbox EventRouter transform
- Kafka consumers project events into read models with at-least-once / idempotent guarantees

### ✔ Hexagonal Architecture

- Domain is isolated from infrastructure
- Application services orchestrate operations
- Infrastructure adapters: REST controllers, JOOQ persistence, mappers, configs

### ✔ Modular Monolith with Spring Modulith

- `account` is a standalone, closed module
- `shared` contains cross-cutting concerns (clock, global error handling, serialization)
- Module boundaries are enforced via architecture tests

---

## 💾 Persistence Layer

### ✔ PostgreSQL (Dockerized)

- Local development uses `docker-compose`
- Schema managed via Flyway migrations
- Event store modeled with `JSONB` payloads and versioning
- WAL configured for logical replication (`wal_level=logical`)

### ✔ JOOQ for type-safe SQL

- Explicit control of queries
- Fine-grained mapping for event serialization/deserialization

---

## 🛠 Local Development Workflow

This project uses PostgreSQL (via `docker-compose`), Flyway (automatic schema migrations), and jOOQ (type-safe SQL with
code generation).

Follow this workflow when you clone the project or when database changes occur.

### 0. Configure Git Hooks

Enable the project's pre-commit hooks to enforce code formatting:

```bash
git config core.hooksPath .githooks
```

This ensures `spotless:check` runs before each commit, preventing unformatted code from entering the repository.

### 1. Start Infrastructure (Docker)

Use the provided `docker-compose.local.yml`:

```bash
docker compose -f docker-compose.local.yml up -d
```

### 2. Register the Debezium Connector

Once Kafka Connect is healthy:

```bash
./debezium/register-connector.sh
```

This registers the outbox CDC connector that captures events from the account.outbox table.

### 3. Apply Flyway migrations

Flyway is executed automatically when Spring Boot starts.

Run the application once:

```bash
mvn spring-boot:run
```

This will:

- connect to the local PostgreSQL instance
- apply all Flyway migrations
- create/update the account schema

You can stop the application once the startup completes.

### 4. Generate jOOQ classes

(only when the database schema changes)

jOOQ code generation is not part of the default Maven lifecycle because it requires a live PostgreSQL database.

After applying new Flyway migrations, regenerate the jOOQ classes with:

```bash
mvn -Pjooq-codegen-local clean generate-sources
```

This updates the generated sources under:

```bash
src/main/generated-jooq/
```

These files are versioned so that CI and other developers can build the project without needing to run jOOQ codegen.

### 5. Build the project

Once the jOOQ sources exist (generated locally or pulled from Git):

```bash
mvn clean install
```

No running database is required for this step.

---

## 🎨 Code Formatting

This project enforces consistent code style using **Spotless** with **Google Java Format**.

### Automatic Formatting

Format all files before committing:

```bash
mvn spotless:apply
```

### Check Formatting

Verify formatting without modifying files:

```bash
mvn spotless:check
```

### Configuration

| Tool        | Standard                  |
|-------------|---------------------------|
| Java        | Google Java Format 1.33.0 |
| Indentation | 2 spaces                  |
| Line length | 100 characters            |
| Imports     | Organized, no wildcards   |
| Files       | UTF-8, LF line endings    |

The formatting rules are defined in:

- `pom.xml` — Spotless plugin configuration
- `.editorconfig` — Editor-agnostic formatting hints

### Important Notes

- **Flyway migrations** (`src/main/resources/db/migration/`) are excluded from formatting — migrations are immutable
  once applied
- **Generated jOOQ classes** (`src/main/generated-jooq/`) are excluded — they are regenerated from the database schema
- The pre-commit hook blocks commits with formatting violations

---

## 🌐 REST API

The contract is defined **OpenAPI-first**, and DTOs/interfaces are code-generated using OpenAPI Generator.

Error handling:

- Global validation errors (`400`)
- Domain rule violations (`422`)
- Resource conflicts (`409`)
- Missing resources (`404`)
- Internal inconsistencies (`500`)

---

## 🧪 Testing Strategy

- **TDD** applied to the entire domain (commands, events, invariants)
- Application service tests with mocked event store
- Web layer tested using `@WebMvcTest`
- Architecture rules enforced with Spring Modulith tests
- Integration tests using real PostgreSQL via Testcontainers
- Kafka consumer integration tests using `@EmbeddedKafka`
- **Mutation testing** with PITest (80% mutation score threshold enforced)

### Mutation Testing

[PITest](https://pitest.org/) is used to verify that tests actually detect code changes (mutations). It targets domain,
application, and utility classes — infrastructure is excluded since those tests require Docker containers.

```bash
mvn pitest:mutationCoverage
```

The HTML report is generated at `target/pit-reports/index.html`.

---

## 🎯 Project Goals

This project is both:

- A **technical playground** to push clean design and strong architectural discipline
- A **realistic financial domain** (similar to private banking account engines)
- A way to demonstrate proficiency with advanced backend concepts:
    - DDD & tactical patterns
    - Event Sourcing & consistency handling
    - Hexagonal + modular monolith
    - Spring Boot 3.3+, JOOQ, Flyway, Postgres, Kafka, Debezium
    - OpenAPI contract-first API design
    - Strong testing practices

---
