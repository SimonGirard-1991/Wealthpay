# WealthPay — Modular Account Domain (DDD + Event Sourcing)

WealthPay is a personal side project focused on designing and implementing a clean and extensible **banking-grade account domain**, using modern architectural practices such as **Domain-Driven Design**, **Event Sourcing**, **CQRS**, and **Modular Monolith** principles with Spring Modulith.

The goal is to build a fully consistent and testable domain for account operations (open, credit, debit, reserve funds, cancel reservation, close account) using techniques common in real financial systems.

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
- Snapshot support planned for large histories

### ✔ CQRS
- Commands mutate state via events
- Queries rely on read projections (to be introduced later)

### ✔ Hexagonal Architecture
- Domain is isolated from infrastructure
- Application services orchestrate operations
- Infrastructure adapters: REST controllers, JOOQ persistence, mappers, configs

### ✔ Modular Monolith with Spring Modulith
- `account` is a standalone, closed module
- `shared` contains cross-cutting concerns (clock, global error handling)
- Module boundaries are enforced via architecture tests

---

## 💾 Persistence Layer

### ✔ PostgreSQL (Dockerized)
- Local development uses `docker-compose`
- Schema managed via Flyway migrations
- Event store modeled with `JSONB` payloads and versioning

### ✔ JOOQ for type-safe SQL
- Explicit control of queries
- Fine-grained mapping for event serialization/deserialization

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

---

## 🎯 Project Goals

This project is both:
- A **technical playground** to push clean design and strong architectural discipline
- A **realistic financial domain** (similar to private banking account engines)
- A way to demonstrate proficiency with advanced backend concepts:
    - DDD & tactical patterns
    - Event Sourcing & consistency handling
    - Hexagonal + modular monolith
    - Spring Boot 3.3+, JOOQ, Flyway, Postgres
    - OpenAPI contract-first API design
    - Strong testing practices

---