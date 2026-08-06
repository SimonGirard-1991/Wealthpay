# Wealthpay — Remediation Plan

Working backlog of known defects, gaps, and hardening items. Each entry is
self-contained: an agent or engineer picking up a single item should not need
any other context to act on it.

**Scope of the assessment that produced this list:** domain, application, and
infrastructure code; Flyway migrations; OpenAPI specs; Kafka/Debezium wiring;
test suite; CI pipeline; Prometheus/Grafana/Alertmanager assets; build and
supply-chain configuration.

---

## How to update this document

Statuses are `TODO` · `IN PROGRESS` · `DONE` · `WONTFIX` · `BLOCKED`.

Confidence markers are:

- **VERIFIED** — the claim was confirmed by executing something (running a
  query, reading the resolved artifact, inspecting bytecode, reproducing the
  behaviour). Not "I read the code and it looked true."
- **REPORTED** — the claim is plausible but was **not** independently
  confirmed. **Verify before writing a fix.** If it proves wrong, mark the item
  `WONTFIX` with the evidence.

### When you treat an item

1. Update the **status** in the item's detail entry.
2. Update the **same status** in the index table. Both must agree.
3. Add a `**Resolved:**` line to the detail entry with the commit SHA and a
   one-line note. For `WONTFIX`, add `**Rationale:**` instead.
4. If work landed that advances an item without closing it, leave the status
   `TODO` and add a `**Partial progress (<sha>).**` line saying what now exists
   and what the item is still waiting on. Do not silently narrow the `Defect`
   description — the gap between the original finding and today is the record.

### When you confirm or refute a REPORTED item

Change `**Confidence:** REPORTED` to `VERIFIED` in the detail entry and state
how it was confirmed. Confidence appears in the detail entry only — it is
deliberately not duplicated in the index.

### Rules

- **Never renumber or reuse IDs.** They are permanent references. If an item is
  dropped, mark it `WONTFIX` — do not delete it.
- **Never delete a resolved entry.** The record of what was fixed and why is the
  point.
- **New items take the next unused ID globally** (currently `WP-122`),
  regardless of tier. The ID blocks below are historical and carry no meaning.
- **Re-tiering an item keeps its ID.** Move the entry and its index row to the
  new section; the number does not change.
- If work on one item reveals a new defect, file it as a new item and
  cross-reference both ways under `**Related:**`.

Locations are given as **file + symbol** rather than line numbers, because line
numbers drift as soon as remediation starts.

---

## Priority tiers

| Tier | Meaning |
|---|---|
| **P0** | Data loss, money loss, or an unauthenticated path to either. Fix before anything else. |
| **P1** | Production-breaking under realistic conditions, or a gap that gets much more expensive to close later. |
| **P2** | Correctness, maintainability, and operability debt. Schedule deliberately. |
| **P3** | Polish, hygiene, and documentation drift. Batch opportunistically. |

---

## Index

### P0 — Correctness & security critical

| ID | Title | Status |
|---|---|---|
| WP-01 | Snapshot failure silently discards a committed command | TODO |
| WP-02 | DLQ is non-functional: DLT topic never declared | TODO |
| WP-03 | DLQ producer cannot serialize the Avro payload | TODO |
| WP-04 | Consumer test masks WP-02 and WP-03 | TODO |
| WP-05 | No authentication or authorization on any endpoint | TODO |
| WP-06 | Live webhook secrets in `.env` require rotation | TODO |

### P1 — Production-breaking / expensive to defer

| ID | Title | Status |
|---|---|---|
| WP-10 | No event schema-evolution story | TODO |
| WP-11 | `SupportedCurrency.CNH` is not a valid ISO 4217 code | TODO |
| WP-12 | Client-supplied amounts are silently rounded | TODO |
| WP-13 | No upper bound on monetary amounts | TODO |
| WP-14 | No idempotency key on `POST /accounts` | TODO |
| WP-15 | No connection-pool sizing or I/O timeouts | TODO |
| WP-16 | No DEFAULT outbox partition | TODO |
| WP-17 | A projection gap freezes an account's read model permanently | TODO |
| WP-18 | Optimistic-lock conflicts are never retried | TODO |
| WP-19 | Concurrent duplicate can be told "already processed" wrongly | TODO |
| WP-20 | Idempotency fingerprint derives from `Record::toString` | TODO |
| WP-21 | Aggregate forgets reservation outcomes | TODO |
| WP-22 | Flyway migrations require superuser privileges | TODO |
| WP-23 | No Debezium heartbeat: replication slot can pin WAL | TODO |
| WP-24 | Kafka Connect is not scraped | TODO |
| WP-25 | `/actuator/prometheus` is anonymous on the application port | TODO |
| WP-26 | Exception handlers leak internal detail on 5xx | TODO |
| WP-27 | No distributed tracing despite observation wiring | TODO |
| WP-28 | Debezium CDC severs trace context | TODO |
| WP-29 | No structured logging, MDC, or correlation ID | TODO |
| WP-30 | No business audit log and no actor attribution on events | TODO |
| WP-31 | Outbox gauge fails into the healthy band and queries on scrape | TODO |
| WP-32 | Liveness and readiness are not distinct; Kafka not health-checked | TODO |
| WP-33 | No rate limiting, request size limits, or CORS policy | TODO |
| WP-34 | No dependency vulnerability scanning | TODO |
| WP-35 | Spring Boot 4.0.2 is behind and the line EOLs 2026-12-31 | TODO |
| WP-36 | Sonar runs but does not gate the build | TODO |
| WP-37 | PITest excludes the entire `customer` bounded context | DONE |
| WP-38 | No enforced coverage threshold | TODO |
| WP-39 | DLQ path has no tests | TODO |
| WP-40 | outbox to Kafka is never tested end to end | TODO |
| WP-41 | event_store and outbox atomicity is untested | TODO |
| WP-42 | No read-model rebuild path, Kafka retention is 24h | TODO |
| WP-115 | Kafka retry backoff is unbounded | TODO |
| WP-116 | No erasure strategy for PII in an immutable event store | TODO |
| WP-117 | No event-store backup, PITR, or restore rehearsal | TODO |
| WP-118 | Flyway core and Postgres plugin are on mismatched major versions | TODO |

### P2 — Correctness & operability debt

| ID | Title | Status |
|---|---|---|
| WP-50 | Currency mismatch bypasses the domain error model | TODO |
| WP-51 | Replay does not validate stream continuity | TODO |
| WP-52 | `updatePhase` ignores the affected row count | TODO |
| WP-53 | Event-store load methods are unbounded | TODO |
| WP-54 | `DataIntegrityViolationException` conflates distinct constraints | TODO |
| WP-55 | `reserveFunds` duplicates `processTransaction` | TODO |
| WP-56 | `Customer.status` is unreachable dead state | DONE |
| WP-57 | Error responses are not RFC 7807 | TODO |
| WP-58 | OpenAPI documents no error responses | TODO |
| WP-59 | Duplicate index on `event_store` | TODO |
| WP-60 | Unused indexes on `outbox` and `processed_reservations` | TODO |
| WP-61 | Idempotency tables grow without bound | TODO |
| WP-62 | Partition drop has no `lock_timeout` | TODO |
| WP-63 | No CHECK constraints on the read model | TODO |
| WP-64 | Debezium credentials hardcoded in a tracked script | TODO |
| WP-65 | Financial payloads written unmasked to technical logs | TODO |
| WP-66 | Three outbox alerts have no dashboard panel | TODO |
| WP-67 | Command dashboard surfaces 1 of 6 outcomes | TODO |
| WP-68 | Alerts and dashboards are not linked | TODO |
| WP-69 | Domain records are mocked in controller tests | TODO |
| WP-70 | No test data builders; fixtures duplicated | TODO |
| WP-71 | Testcontainers restarts Postgres per test class | TODO |
| WP-72 | CI lacks permissions, timeout, and concurrency controls | TODO |
| WP-73 | GitHub Actions pinned to mutable tags | TODO |
| WP-74 | `maven-enforcer-plugin` absent | TODO |
| WP-75 | Unused declared dependencies | TODO |
| WP-76 | Snapshot restore and projection replay untested end to end | TODO |
| WP-77 | Documented "snapshot never blocks" behaviour is untested | TODO |
| WP-78 | Builds are not reproducible | TODO |
| WP-79 | Kafka listener latency has no percentiles | TODO |
| WP-119 | `Money`'s arithmetic and comparisons have no direct tests | TODO |

### P3 — Hygiene & polish

| ID | Title | Status |
|---|---|---|
| WP-90 | `ReservationId` is UUIDv7 but externally exposed | TODO |
| WP-91 | ADR-008 "SLOs" are cause-based thresholds | TODO |
| WP-92 | `last_updated_at` never refreshed on projection update | TODO |
| WP-93 | Read-side query lacks `@Transactional(readOnly = true)` | TODO |
| WP-94 | `assert` used where assertions are disabled | TODO |
| WP-95 | One migration creates an unqualified table | TODO |
| WP-96 | Append-only trigger asymmetry is undocumented | TODO |
| WP-97 | Consumer `isolation.level` left implicit | TODO |
| WP-98 | Redundant `SELECT max(version)` on every append | TODO |
| WP-99 | `AccountEventMeta` admits `version == 0` | TODO |
| WP-100 | `Account.toSnapshot` is a static taking an `Account` | TODO |
| WP-101 | `AccountSnapshot` NPEs on null reservations | TODO |
| WP-102 | `Account` has no identity-based `equals`/`hashCode` | TODO |
| WP-103 | `AccountEventPublisher` is misnamed | TODO |
| WP-104 | `ReservationOutcome` does not defensively copy | TODO |
| WP-105 | Customer BC lacks an ID-generator port | TODO |
| WP-106 | Documentation drift in README and CLAUDE.md | TODO |
| WP-107 | UUIDv7 generator tests assert version, not monotonicity | TODO |
| WP-108 | No `.env.example` template | TODO |
| WP-109 | Spotless ratchet leaves the existing tree unchecked | TODO |
| WP-110 | Gatling assertions never run in CI | TODO |
| WP-111 | No SBOM generation | TODO |
| WP-112 | No failsafe/surefire split | TODO |
| WP-113 | Sonar organization declared in two places | TODO |
| WP-114 | CI uses bare `mvn`, not the pinned wrapper | TODO |
| WP-120 | PITest cannot see record compact constructors (measured: low impact) | WONTFIX |
| WP-121 | `INCREMENTAL_DOMAIN_ONLY_BCS` is an ArchUnit exemption with no expiry | TODO |

---

# P0 — Correctness & security critical

## WP-01 — Snapshot failure silently discards a committed command

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-19, WP-77

**Where:** `account/application/AccountApplicationService` → `saveEvents`;
`account/infrastructure/db/repository/AccountSnapshotRepository` → `saveSnapshot`

**Defect.** `saveEvents` wraps the snapshot write in
`catch (RuntimeException)` and logs a warning, on the stated grounds that a
snapshot is a performance optimization that must not block the critical path.
`AccountSnapshotRepository.saveSnapshot` carries no `@Transactional`, so it runs
on the ongoing transaction's connection and is the **last statement** in the
command transaction.

In PostgreSQL, once any statement inside a transaction fails, the backend enters
aborted state (`25P02`) and a subsequent `COMMIT` is executed as `ROLLBACK` —
and pgjdbc does **not** throw on that commit.

**Impact.** A database-level snapshot failure (lock timeout, `statement_timeout`,
deadlock on the upsert's row lock) discards the event rows, the outbox rows, and
the idempotency row, while returning **HTTP 200 `COMMITTED`** to the client and
recording `outcome=committed` on the command timer. Money movement acknowledged
and never persisted, with no alert.

**Fix.** Take the snapshot off the command transaction. Preferred:

```java
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
      @Override public void afterCommit() { trySaveSnapshot(account); }
    });
```

Alternatives: `@Transactional(propagation = REQUIRES_NEW)` on `saveSnapshot`
(separate connection, cannot poison the outer one), or a JDBC `SAVEPOINT`
immediately before the insert.

**Done when.** A Testcontainers test injects a snapshot store that fails at the
database level, and asserts the event row **is** present after commit and the
command reports success. See WP-77.

---
---

## WP-02 — DLQ is non-functional: DLT topic never declared

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-03, WP-04, WP-17, WP-39, WP-115

**Where:** `shared/config/KafkaErrorConfig` → `errorHandler`;
`docker-compose.local.yml` (broker environment)

**Defect.** `DeadLetterPublishingRecoverer` resolves its destination to
`wealthpay.AccountEvent.DLT` by default. That topic is never declared — there is
no `NewTopic`, `TopicBuilder`, or `KafkaAdmin` bean anywhere in `src/main`, and
`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"` is set on all three brokers.
Debezium's `topic.creation.enable` covers only connector-produced topics, not
the DLT.

`DeadLetterPublishingRecoverer` defaults to `verifyPartition=true` and
`failIfSendResultIsError=true`, so recovery blocks on `partitionsFor()`
(`max.block.ms`, 60s default), fails with `UnknownTopicOrPartitionException`,
and rethrows. The record is never recovered and the offset never advances.

**Impact.** Every message classified non-retryable — the only poison-message
path the design has — wedges its partition permanently in a 60-second failure
loop. Because ordering is per-account-per-partition, one bad event freezes the
read model for **every account on that partition**.

**Fix.** Declare the topic. Partition count must be at least the source topic's
3, because `verifyPartition` maps DLT partition to source partition.

```java
@Bean
NewTopic accountEventDlt() {
  return TopicBuilder.name("wealthpay.AccountEvent.DLT")
      .partitions(3).replicas(3).build();
}
```

Add a Prometheus alert on DLT depth — `docker/prometheus/rules/kafka-consumer.yml`
currently has none.

**Done when.** WP-02, WP-03, and WP-04 all land together — fixing one third of
the DLQ does not make it work. A poison record published to the source topic is
observed on the DLT with its original payload and the
`kafka_dlt-exception-fqcn` header intact.

---
---

## WP-03 — DLQ producer cannot serialize the Avro payload

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-02, WP-04, WP-17, WP-39, WP-115

**Where:** `shared/config/KafkaErrorConfig`; `src/main/resources/application.properties`

**Defect.** The consumer uses `KafkaAvroDeserializer` with
`specific.avro.reader=false`, so record values arrive as
`org.apache.avro.util.Utf8`. No `spring.kafka.producer.*` is configured, and
Spring Boot's `KafkaProperties$Producer` initializes **both** key and value
serializers to `StringSerializer` (confirmed at bytecode level against the
resolved `spring-boot-kafka` artifact for this project's Boot version).

`DeadLetterPublishingRecoverer` forwards the original `record.value()`
unchanged, so `StringSerializer` receives a `Utf8` — which implements
`CharSequence`, not `String` — and throws `ClassCastException` inside `send()`.

**Impact.** Even with WP-02 fixed, no message can reach the DLT.

**Fix.** Set
`spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.ByteArraySerializer`
and give the recoverer a matching `KafkaTemplate<String, byte[]>`, or configure
a dedicated DLT template.

**Done when.** An integration test publishes a genuinely Avro-encoded poison
record and observes it on the DLT.

---
---

## WP-04 — Consumer test masks WP-02 and WP-03

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-02, WP-03, WP-39

**Where:** `src/test/java/.../infrastructure/consumer/AccountOutboxConsumerTest`

**Defect.** The test's `@EmbeddedKafka` properties override
`spring.kafka.consumer.value-deserializer` to `StringDeserializer`. Production
uses `KafkaAvroDeserializer`. The test therefore never exercises the Avro path
— the one dimension in which the DLQ is broken.

**Impact.** This is why two independent production defects sat undetected behind
a green suite. A test that diverges from production configuration in a
load-bearing dimension provides false assurance.

**Fix.** Either run the test against the real Avro deserializer with a mock
schema registry, or add a second test class that does, so the production
serialization path is covered.

**Done when.** At least one consumer test runs with the production
deserializer configuration.

---
---

## WP-05 — No authentication or authorization on any endpoint

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-13, WP-25, WP-30, WP-33, WP-75, WP-106

**Where:** `pom.xml` (no `spring-boot-starter-security`);
`src/main/resources/openapi/account/account-api.yaml` (no `securitySchemes`)

**Defect.** `spring-boot-starter-security` is not on the main classpath;
`spring-security-test` sits unused in test scope. No `SecurityFilterChain`, no
`@EnableWebSecurity`, no `@PreAuthorize` anywhere. All eight endpoints are
anonymous:

| Endpoint | Anonymous capability |
|---|---|
| `POST /accounts` | Mint unlimited accounts |
| `GET /accounts/{id}` | Read any balance, reserved amount, status |
| `POST /accounts/{id}/deposits` | Credit any account |
| `POST /accounts/{id}/withdrawals` | **Debit any account** |
| `POST /accounts/{id}/reservations` | Reserve funds on any account |
| `.../reservations/{rid}/capture` | **Irreversible money movement** |
| `.../reservations/{rid}/cancel` | Release any hold |
| `POST /accounts/{id}/close` | Close any account |

The only control is UUID unguessability, which is a bearer token with no
rotation, no revocation, and no expiry — printed into every access log and
`Location` header.

**Impact.** Total IDOR across all money-moving operations, and no attribution on
any transaction. When a balance is wrong there is no record of who moved it,
which is an audit failure independent of the theft risk.

**Fix.** Four layers, all required:

1. Add `spring-boot-starter-security` and resource-server JWT validation with an
   algorithm allowlist. Deny-by-default `SecurityFilterChain`; permit only
   `/actuator/health/**`.
2. Declare `securitySchemes` in the OpenAPI specs so the generated interfaces
   carry it and the published contract is honest.
3. **Ownership check, not just authentication.** A valid token for account A
   must not move account B. Enforce in `AccountApplicationService` — pass the
   authenticated `CustomerId` into the command and assert ownership before
   `handle(...)`. Enforcing this in a filter re-introduces the IDOR the moment
   someone adds an endpoint.
4. Add an ArchUnit rule asserting every `@RestController` method is covered by
   an authorization annotation, so this cannot silently regress.

**Sequencing note.** Land this with the in-flight `customer` bounded context.
`docs/context-map.md` already establishes Customer as the upstream authority
over identity, which is where account ownership belongs.

**Done when.** An unauthenticated request to any account endpoint returns 401,
a token scoped to account A returns 403 on account B, and the ArchUnit rule is
green.

---
---

## WP-06 — Live webhook secrets in `.env` require rotation

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-108

**Where:** `.env`; `.githooks/pre-commit`

**Defect (unconfirmed).** `.env` is reported to contain two live Discord webhook
URLs with full tokens, at file mode `644`. **This was not independently
verified — confirm the file contents before acting.**

What *is* confirmed: `.env` is correctly ignored via a bare `.env` pattern in
`.gitignore`, is untracked, and has never appeared in git history. The hygiene
around committing is correct.

**Impact.** A Discord webhook URL is a bearer credential. Anyone holding one can
post arbitrary messages into the alerting channel — including **forged
"resolved" notifications**, which turns the alerting pipeline into an
attacker-controlled channel during an incident. Mode `644` makes it
world-readable on a shared machine.

**Fix.**

1. Rotate both webhooks. Treat them as disclosed regardless of the verification
   outcome — rotation is cheap and the downside of assuming otherwise is not.
2. `chmod 600 .env`.
3. Add secret scanning (`gitleaks` or `detect-secrets`) to `.githooks/pre-commit`
   — it currently runs only `spotless:check`, so nothing prevents a
   `git add -f .env`.
4. Move to a secret manager for anything beyond local development.

**Done when.** Webhooks rotated, file mode corrected, and the pre-commit hook
fails on a staged secret.

---

# P1 — Production-breaking / expensive to defer

## WP-10 — No event schema-evolution story

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-17

**Where:** `account/infrastructure/db/repository/AccountEventRepository`;
`.../mapper/EventStoreEntryToAccountEventMapper`;
`account/infrastructure/serialization/AccountEventType`; `shared/utils/MapperUtils`

**Defect.** Three compounding gaps:

- The persisted event type is `event.getClass().getSimpleName()` — the Java
  class name **is** the durable wire contract. A rename makes every historical
  row unreadable (`AccountEventType.valueOf` throws).
- The JSON payload carries **no version field**. Snapshots gained
  `schema_version` in `V12`; events, which are permanent, did not.
- The reader is strict, not tolerant: `getRequiredField` throws on a missing
  field.

**Impact.** The first time a required field is added to an existing event type,
every pre-existing row fails deserialization, `Account.rehydrate` throws, and
**every affected account becomes permanently unloadable** — reads and writes
both. In event sourcing the store is forever; this is the hardest decision to
retrofit.

**Fix,** in order of cost:

1. Add `payload_version SMALLINT NOT NULL DEFAULT 1` to `account.event_store`
   and write it explicitly.
2. Decouple the wire name from the class name — an explicit `String wireName()`
   on each event, or a `Map<Class<?>, String>` registry.
3. Introduce an upcaster seam in `infrastructure/db/repository/mapper`:
   `JsonNode upcast(String type, int fromVersion, JsonNode payload)` applied
   before the mapping switch. An empty chain today makes the future change
   additive rather than a migration.
4. Make new fields optional-with-default on read (tolerant reader).

**Do this before the next event-shape change, not after.**
`docs/adr/004-event-contract.md` already documents this gap honestly — update it
when the fix lands.

**Done when.** A test writes an event at version 1, adds a required field at
version 2, and asserts both replay correctly.

---
---

## WP-11 — `SupportedCurrency.CNH` is not a valid ISO 4217 code

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `account/domain/model/SupportedCurrency`;
`src/main/resources/openapi/account/account-api.yaml` (currency enum)

**Defect.** Confirmed against the running JDK:
`Currency.getInstance("CNH")` throws
`IllegalArgumentException: The input currency code: "CNH" is not a valid ISO 4217 code`.
`Money`'s compact constructor calls
`currency.toJavaCurrency().getDefaultFractionDigits()` unconditionally, so
**every** `Money` construction with CNH throws. CNH (offshore CNY) is a market
convention, not an ISO code.

**Impact.** The API publicly advertises a currency that cannot be used. Any
client selecting it receives a 400 whose body leaks a raw JDK message.

**Fix.** Replace `CNH` with `CNY` in the enum and the OpenAPI enum. Cache the
`Currency.getInstance` lookup in a field while there — it currently runs on
every `Money` construction.

**Done when.** This test passes:

```java
@Test
void every_supported_currency_resolves_to_an_iso_4217_currency() {
  assertThatNoException().isThrownBy(() ->
      Arrays.stream(SupportedCurrency.values()).forEach(SupportedCurrency::toJavaCurrency));
}
```

---
---

## WP-12 — Client-supplied amounts are silently rounded

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-13

**Where:** `account/domain/model/Money` (compact constructor);
`account/infrastructure/web/mapper/MoneyDtoToDomainMapper`;
`account-api.yaml` — **four** amount fields: `amount` on deposits, withdrawals,
and reservations, plus `initialAmount` on account opening

**Defect.** The compact constructor applies
`amount.setScale(fractionDigits, RoundingMode.HALF_EVEN)` unconditionally. No
amount schema constrains precision. A client posting `10.5011` is debited
`10.50` with a `200 OK` and no indication anything changed.

**Impact.** HALF_EVEN is correct for *derived* amounts (interest, FX,
allocation) and wrong for an *inbound instruction*. In a ledger this is a
reconciliation and compliance defect: the instruction on file differs from the
instruction received, with no audit record of the change. `Money`'s own Javadoc
states the type is for posted amounts, which is the argument for rejecting
over-precise input rather than normalizing it.

**Fix.** The **domain check is the authoritative guard** — implement it first
and treat any transport-level validation as defence in depth:

```java
if (amount.stripTrailingZeros().scale() > defaultFractionDigits) {
  throw new AmountPrecisionException(amount, currency);   // → 422
}
```

Optionally add `multipleOf: 0.01` to the OpenAPI amount schemas. Two caveats:
`multipleOf` cannot express JPY's zero fraction digits, and it has no
Bean Validation equivalent, so openapi-generator may emit nothing enforceable.
Do not rely on it.

**Done when.** An over-precise amount returns **422** from the domain guard, and
no rounding occurs. If transport validation is also added and returns 400
first, update this criterion to match rather than leaving both claimed.

---
---

## WP-13 — No upper bound on monetary amounts

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-05, WP-12, WP-33

**Where:** `account-api.yaml` — `amount` on deposits, withdrawals, and
reservations uses `exclusiveMinimum: 0`; `initialAmount` uses `minimum: 0`.
**Neither keyword sets a maximum**, and the two use different lower-bound
semantics.

**Defect.** No upper bound on any monetary field.

**Impact.** An unbounded transaction amount in a banking API is a business-rule
gap on an unauthenticated endpoint (WP-05). Values are only rejected later by
`NUMERIC(19,4)`, producing a 500 rather than a validation error. Extremely large
exponents also force `BigDecimal.setScale` to materialize the full digit
expansion, though a meaningful resource impact requires an exponent far larger
than typical fuzzing input — treat this as a secondary concern, not the primary
justification.

**Fix.** Add a `maximum` to all four amount schemas (choose the ledger's real
ceiling) and align the lower-bound keyword across them. Add a domain-side guard
in the `Money` compact constructor so the bound holds regardless of transport.

**Done when.** An out-of-range amount is rejected at both the transport and
domain layers, with a test for each, and all four schemas agree on bounds.

---
---

## WP-14 — No idempotency key on `POST /accounts`

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `account-api.yaml` → `openAccount`;
`account/application/AccountApplicationService` → `openAccount`

**Defect.** Credit, debit, and reserve all require a `Transaction-Id` header
routed through `ProcessedTransactionStore`. `openAccount` has none — it mints a
fresh `AccountId` and appends unconditionally. Capture, cancel, and close are
naturally idempotent via aggregate state and are fine.

**Impact.** A client retry after a timeout or a load-balancer 502 creates a
**second account** with a duplicate opening balance, silently.

**Fix.** Accept the same idempotency header on account opening and route it
through `ProcessedTransactionStore.register` before minting the ID, returning
the existing `AccountId` with `201` and the original `Location` on `NO_EFFECT`.
This needs a `transaction_id → account_id` lookup, mirroring `lookupReservation`.

**Done when.** Two identical open requests with the same key produce one account
and the same response body.

---
---

## WP-15 — No connection-pool sizing or I/O timeouts

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `src/main/resources/application.properties` — zero `hikari` keys, no
`socketTimeout` on the JDBC URL

**Defect.** Two compounding problems:

- `spring.threads.virtual.enabled=true` with the default
  `maximumPoolSize=10`. Virtual threads remove the *thread* ceiling but not the
  *connection* ceiling.
- No `socketTimeout`, `statement_timeout`, `lock_timeout`, or
  `idle_in_transaction_session_timeout` anywhere.

**Impact.** A traffic burst piles thousands of virtual threads onto ten
connections, all blocked in `getConnection()` — a latency cliff with no
shedding. The `DbConnectionsNearMax` alert measures *server-side* connections
and will not move, because the queue is client-side and invisible. Separately, a
network partition leaves a thread blocked in `read()` until TCP retransmission
gives up, roughly 15 minutes on Linux.

**Fix.**

```properties
spring.datasource.url=${DB_URL}?socketTimeout=30&connectTimeout=10&tcpKeepAlive=true
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.connection-timeout=3000
spring.datasource.hikari.max-lifetime=1500000
spring.datasource.hikari.leak-detection-threshold=20000
spring.datasource.hikari.connection-init-sql=SET statement_timeout = '10s'
```

Size the pool from a measured profile, not the number above.

**Done when.** Pool and timeouts are explicit, and a load test shows bounded
latency degradation rather than a cliff.

---
---

## WP-16 — No DEFAULT outbox partition

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-31, WP-62

**Where:** `db/migration/account/V13__partition_outbox.sql`;
`V16__outbox_cleanup_observability.sql` → `manage_outbox_partitions`

**Defect.** `account.outbox` is `PARTITION BY RANGE (occurred_at)` with
partitions pre-created for `CURRENT_DATE..+7`. There is no DEFAULT partition. If
`manage_outbox_partitions` fails to run for eight consecutive days, `INSERT`
fails with `no partition of relation "outbox" found for row`.

**Impact.** That error propagates out of `OutboxRepository.publish` and rolls
back the entire `@Transactional` command — so **every write operation fails**,
not just the messaging relay. A storage-management concern becomes a total write
outage. The cleanup function deliberately swallows failures with
`EXCEPTION WHEN OTHERS`, so the only signal is a `failure` row polled every five
minutes.

**Fix.** The DEFAULT partition is the right call, but **it is not a one-liner and
a naive version makes things worse.** All four parts below are required.

1. **Add the partition**, in a new migration with its own lock guard:

   ```sql
   SET LOCAL lock_timeout = '5s';
   CREATE TABLE account.outbox_default PARTITION OF account.outbox DEFAULT;
   ```

   Rows landing there are still published correctly — the publication uses
   `publish_via_partition_root`.

2. **Broaden the partition-creation error handling in `manage_outbox_partitions`.**
   This is the part that matters. Once any row sits in `outbox_default` for date
   D, `CREATE TABLE ... PARTITION OF ... FOR VALUES FROM (D) TO (D+1)` must
   re-check the DEFAULT partition and fails with **`23514` / `check_violation`**:

   ```
   ERROR: updated partition constraint for default partition "outbox_default"
          would be violated by some row
   ```

   The current inner handler catches only `duplicate_table`, so `23514` escapes
   to the function-level `EXCEPTION WHEN OTHERS`, which rolls back the **entire**
   function — including the partition drops. Retention dies permanently, and the
   cleanup regex `^outbox_\d{4}_\d{2}_\d{2}$` never matches `outbox_default`, so
   nothing ever removes those rows. **A bounded, loud write outage would be
   traded for an unbounded, silent storage leak.** Catch `check_violation`,
   log it, and continue to the drop phase.

3. **Document a drain procedure** as the mandatory recovery step, in
   `docs/adr/007-outbox-lifecycle-management.md` or a runbook — an operator will
   need it under time pressure. The correct order is:

   ```
   DETACH outbox_default
     → CREATE the missing dated partitions (now possible: the default is gone)
     → DROP the detached table
     → ATTACH a fresh empty DEFAULT
   ```

   **Drop the detached rows; do not re-insert them.** They were already relayed
   to Kafka when first inserted — the publication is `FOR TABLE account.outbox`
   and covers all partitions implicitly. Re-inserting generates fresh WAL and
   Debezium re-publishes every row as a duplicate. This is the same reasoning
   `V13__partition_outbox.sql` gives for refusing to backfill from `outbox_old`:
   *"the event store is the source of truth; the outbox is an ephemeral CDC
   relay, not an archive."*

   (If a future requirement genuinely demands moving rows rather than dropping
   them, note that `outbox_id` is `GENERATED ALWAYS AS IDENTITY`, so a bare
   `INSERT ... SELECT *` fails with `428C9` — enumerate columns or use
   `OVERRIDING SYSTEM VALUE`.)

4. **Alert on `outbox_default` occupancy — and understand why this is not
   optional.** Step 2 changes the failure signal: today `check_violation`
   escapes to the function-level handler, which writes a `'failure'` row and
   fires the existing `OutboxCleanupFailed` alert. Once block A swallows it, the
   function falls through to its success branch and writes `'success'` instead —
   so **partition creation silently stops while the existing alert stays
   green.** Step 2 must not ship without step 4.

   Implement the gauge by extending `OutboxCleanupObserver`, which already polls
   on `@Scheduled` into an `AtomicLong`. Do **not** add a scrape-time query
   (that is the WP-31 anti-pattern), and do not grow `sql-exporter` — its own
   config header restricts it to documented postgres-exporter coverage gaps.

**Lock note.** `CREATE TABLE ... PARTITION OF` takes `ACCESS EXCLUSIVE` on
`outbox_default` on **every subsequent daily run**, not only at attach time.
That is a recurring cost, not a one-off. WP-62's `SET LOCAL lock_timeout` covers
the function; the migration in step 1 needs its own, as shown.

**Note on existing alerting.** `OutboxCleanupFailed`
(`outbox_cleanup_last_status == 0`) **already exists** in
`docker/prometheus/rules/wealthpay.yml` — do not add a duplicate.

**Done when.** The DEFAULT partition exists, `manage_outbox_partitions` survives
a day with rows in it (verified by test or rehearsal), the drain procedure is
documented, and an alert fires on non-zero `outbox_default` occupancy.

---
---

## WP-17 — A projection gap freezes an account's read model permanently

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-02, WP-03, WP-10, WP-115

**Where:** `account/infrastructure/db/repository/AccountBalanceReadModel`;
`shared/config/KafkaErrorConfig`

**Defect (verify first).** The projector throws `IllegalStateException` on a
non-contiguous version, and `IllegalStateException` is registered as
**non-retryable**. So if event N+1 is ever diverted (unknown event type, missing
header, unsupported currency), every subsequent event for that account also
fails the contiguity check and is also classified non-retryable — forever.

The gap check itself is correct and valuable. The problem is that the *reaction*
is indistinguishable from a malformed-message failure.

**Impact.** The account's balance view freezes at version N with no error
surfaced to callers of `GET /accounts/{id}` — indefinitely stale balances served
as if current.

**Fix.** Introduce a dedicated `ProjectionGapException` classified as
**retryable with bounded attempts**, so transient reordering self-heals and a
genuine gap eventually reaches the DLT with a distinct type. Add a
`wealthpay.projection.gap` counter and alert on it — a frozen read model in a
banking system warrants a page.

**Sequencing.** Depends on WP-02 and WP-03 (a DLT classification is meaningless
while the DLQ cannot publish) **and on WP-115** — "bounded attempts" does not
currently exist, so this fix cannot work until the backoff is bounded.

**Done when.** A deliberately skipped event produces bounded retries, then a
DLT record with the gap-specific exception type, and the counter increments.

---
---

## WP-18 — Optimistic-lock conflicts are never retried

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `account/infrastructure/db/repository/AccountEventRepository`;
`shared/infrastructure/web/GlobalExceptionHandler`

**Defect.** `OptimisticLockingFailureException` propagates straight to the
client as `409 Conflict`. There is no retry anywhere — no `@Retryable`, no
`RetryTemplate`.

**Impact.** Two concurrent credits to the same account do not conflict
*semantically*; they conflict only on the version counter. Reloading and
re-handling would succeed. Under real concurrency on a hot account (merchant
settlement, treasury) this pushes a meaningful 409 rate onto clients who can do
nothing but retry.

**Fix.** Wrap the command in a bounded retry with jitter, **outside** the
transaction. A self-invoked `@Retryable` on the same bean will not work — the
transaction proxy is on that bean — so put the retry on a thin delegating facade
or use an explicit `RetryTemplate`. Retry only on
`OptimisticLockingFailureException`, cap at roughly 3 attempts, keep 409 as the
terminal outcome. The `outcome=concurrency_conflict` timer tag already exists,
so the improvement is directly measurable.

If the current behaviour is kept deliberately, document it and make the 409 body
tell the client it is safe to retry with the same idempotency key.

**Done when.** A concurrent-write test shows the second writer succeeding after
retry, and the conflict metric drops.

---
---

## WP-19 — Concurrent duplicate can be told "already processed" wrongly

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-01

**Where:** `account/infrastructure/db/repository/ProcessedTransactionRepository`

**Defect (verify first).** Under READ COMMITTED, when two requests carry the
same `transactionId`:

- `INSERT ... ON CONFLICT DO NOTHING` does not block on a conflicting
  *uncommitted* row — it returns nothing immediately.
- The `UNION ALL` branch cannot see the uncommitted row (same snapshot).
- The fallback fingerprint read opens a fresh snapshot, but if the first
  transaction still has not committed, it also returns null.
- With no stored fingerprint, no conflict is raised and the method returns
  `NO_EFFECT`.

**Impact.** The second request reports "already applied" and appends nothing. If
the first transaction then rolls back — via WP-01, a constraint failure, or a
dropped connection — **the credit or debit is lost while the client was told it
succeeded.**

**Fix.** Make the loser wait rather than guess. Either:

- Use `ON CONFLICT ... DO UPDATE SET transaction_id = EXCLUDED.transaction_id RETURNING ...`
  — the `DO UPDATE` form takes a row lock and blocks until the other transaction
  resolves, yielding the true committed state. (This was deliberately rejected
  for WAL and dead-tuple cost; that cost is real but far smaller than a lost
  payment.) Or:
- Keep `DO NOTHING`, but when both the insert and the fallback return null,
  throw a retryable conflict rather than returning `NO_EFFECT`. **Never report
  success for a state that could not be observed.**

**Done when.** A Testcontainers test runs two concurrent transactions on the
same `transactionId` where the first rolls back, and asserts the second does not
report `NO_EFFECT`.

---
---

## WP-20 — Idempotency fingerprint derives from `Record::toString`

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-119

**Where:** `account/domain/command/AccountTransaction` → `fingerprint`

**Additional evidence.** `fingerprint()` carries a surviving mutant —
`replaced return value with ""` — meaning the digest can be a constant empty
string with the suite green. Nothing pins the digest input today, which is the
same gap this item's "Done when" closes. (`AccountTransaction` is an *interface*;
`fingerprint` is a `default` method, so this survivor is unrelated to the record
filtering discussed in WP-120.)

**Defect.** The SHA-256 digest is computed over `toString()`. `Record.toString()`
is explicitly documented as implementation-dependent and free to vary between
implementations — yet it is persisted as a durable contract in
`processed_transactions.fingerprint`.

**Impact.** Adding, renaming, or reordering a single record component changes
every fingerprint. A legitimate client retry of an in-flight transaction across
a deploy then raises a conflict → **409 on a correct retry**, which is precisely
the failure the mechanism exists to prevent. A JDK or compiler change has the
same effect.

**Fix.** Build the canonical string explicitly and version it:

```java
"v1|%s|%s|%s|%s|%s".formatted(
    getClass().getSimpleName(), accountId().id(), transactionId().id(),
    money().amount().toPlainString(), money().currency().name());
```

Also: cache the `MessageDigest` provider lookup, and replace the empty
`IllegalStateException("")` message. Consider moving fingerprinting to the
application layer — it is an idempotency concern, not a domain invariant, and
lives oddly as a `default` method on a domain command interface.

**Migration note.** Changing the algorithm invalidates every stored fingerprint.
Either accept a one-time window where in-flight retries conflict, or store the
algorithm version alongside the hash and accept both during a transition.

**Done when.** The digest input is explicitly enumerated and a test pins the
hash for a known command.

---
---

## WP-21 — Aggregate forgets reservation outcomes

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-52

**Where:** `account/domain/model/Account` → `apply`;
`account/application/AccountApplicationService` → reservation handling;
`ReservationStoreInconsistencyException`

**Defect (verify first).** `apply()` removes the reservation from the map on
both `ReservationCanceled` and `ReservationCaptured`. The aggregate therefore
cannot distinguish "already captured" from "already canceled". The rule "you may
not capture a canceled reservation" is consequently re-implemented in the
**application service** against the `processed_reservations` side table.

The strongest evidence this is wrong is `ReservationStoreInconsistencyException`,
which exists solely to handle a split-brain where the side table and the
aggregate disagree. A "should never happen" branch someone had to write is a
modelling smell.

**Impact.** `processed_reservations` is a projection-shaped table, not the event
stream. If it drifts (partial restore, manual fix, a bug in `updatePhase` — see
WP-52) the capture/cancel rule silently changes behaviour, and the event stream
can no longer replay the decision.

**Fix.** Keep terminal state in the aggregate:

```java
record ReservationState(Money amount, ReservationPhase phase) {}
private final Map<ReservationId, ReservationState> reservations = new HashMap<>();
```

Transition the phase instead of removing the entry; `totalReservedFunds()` sums
only `RESERVED`. Then the domain throws `ReservationAlreadyCanceledException`,
the application-side idempotency check collapses,
`ReservationStoreInconsistencyException` can be deleted, and
`processed_reservations` becomes a pure lookup index.

**Trade-off to manage.** Unbounded map growth on hot accounts — prune terminal
reservations older than the dispute window at snapshot time, as a deliberate
policy rather than accidental forgetting.

**Done when.** Capturing a canceled reservation is rejected by a pure domain
test with no application-layer or database involvement.

---
---

## WP-22 — Flyway migrations require superuser privileges

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-118

**Where:** `db/migration/account/V15__outbox_publication.sql`;
`V16__outbox_cleanup_observability.sql`

**Defect (verify first).** `CREATE PUBLICATION ... FOR ALL TABLES` requires
superuser; the later `DROP`/`CREATE PUBLICATION FOR TABLE` requires ownership of
both the publication and the table. Locally Flyway runs as the database owner,
so this works.

**Impact.** On RDS, Aurora, or Cloud SQL, running schema migrations as a
superuser-equivalent role is either prohibited or a policy violation. The first
real production deploy fails at V15.

**Fix.** Move publication and replication-slot lifecycle out of Flyway into a
one-time, separately privileged provisioning step (Terraform or a DBA runbook).
Keep Flyway to DDL a plain schema-owner role can execute. Set
`publication.autocreate.mode: filtered` on the connector so Debezium never
attempts a `FOR ALL TABLES` create itself.

Migrations are immutable — do not edit V15. Fix forward with a guarded
replacement and document the provisioning prerequisite.

**Done when.** A migration run as a non-superuser schema owner succeeds against
a clean database.

---
---

## WP-23 — No Debezium heartbeat: replication slot can pin WAL

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-117

**Where:** `debezium/register-connector.sh`; `docker-compose.local.yml`
(Postgres configuration)

**Defect (verify first).** The publication is narrowed to `account.outbox` only.
Debezium advances `confirmed_flush_lsn` only when it emits records. During any
period where the outbox is idle but the rest of the database is busy — event
store writes, idempotency tables, autovacuum — the slot's LSN stagnates while
WAL accumulates. `heartbeat.interval.ms` is not set, and
`max_slot_wal_keep_size` is not set on the server either.

**Impact.** The most common way a Debezium deployment fills a production disk.

**Note.** Detection is already strong — `ReplicationSlotWalRetentionHigh`
(correctly measured on `restart_lsn`) and `ReplicationSlotInactive` both exist.
This item is about *prevention*, so the outcome is a non-event rather than a
3am page.

**Fix.** Add to the connector configuration:

```json
"heartbeat.interval.ms": "10000",
"heartbeat.action.query": "INSERT INTO account.debezium_heartbeat (ts) VALUES (now()) ON CONFLICT ((true)) DO UPDATE SET ts = now()"
```

Set `max_slot_wal_keep_size = '10GB'` on the server so a dead connector degrades
the pipeline instead of the database.

**Done when.** Slot LSN advances during an outbox-idle period under load on
other tables.

---
---

## WP-24 — Kafka Connect is not scraped

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `docker/prometheus/prometheus.yml`

**Defect.** Scrape jobs exist for the application, Alertmanager,
postgres-exporter, and sql-exporter. There is **no Kafka Connect job**.

**Impact.** If the Debezium task transitions to `FAILED`, nothing alerts. The
failure is doubly silent: writes keep succeeding (the outbox insert is a plain
database write), the consumer sees no records so `HighConsumerLag` never fires,
and the read model quietly diverges from the event store. The compose
healthcheck catches it locally but has no production analogue.

**Fix.** Expose the Connect JMX metrics
(`kafka.connect:type=connector-task-metrics`) and alert on
`connect_connector_status != running`, plus
`connect_source_task_source_record_poll_rate == 0` while the outbox insert rate
is non-zero.

**Done when.** Stopping the connector fires an alert within one evaluation
window.

---
---

## WP-25 — `/actuator/prometheus` is anonymous on the application port

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-05

**Where:** `src/main/resources/application.properties`

**Defect.** `management.endpoints.web.exposure.include=health,prometheus` is
appropriately narrow — `/actuator/modulith` is correctly not exposed despite
`spring-modulith-actuator` being a runtime dependency. But no
`management.server.port` is set, so both endpoints sit on the public 8080
alongside the banking API, with no authentication (WP-05).

**Impact.** Anonymous readers obtain every endpoint URI template, JVM and heap
internals, exact pool sizing, `outbox_row_count_estimate` (business volume), and
per-command transaction rates and outcomes — i.e. live transaction volume and
failure profile.

**Fix.** Move actuator to its own port and keep only `health` on the public one.
Add `management.endpoint.health.show-details=when-authorized` once WP-05 lands.

**Pick the port carefully.** `9090` is taken by Prometheus and `8081` by the
schema registry in `docker-compose.local.yml`, and the application runs on the
host via `mvn spring-boot:run` — so both collide. Use `9091` or another free
port.

**Bind address differs by environment.** `management.server.address=127.0.0.1`
is right in production (scrape via sidecar or mesh) but breaks local
development: `docker/prometheus/prometheus.yml` scrapes
`host.docker.internal:8080` from inside a container, which loopback-only
rejects. Bind to all interfaces locally and restrict in the production profile.

**Blast radius — do not skip.** Moving the management port breaks:

- the `wealthpay` scrape job target in `docker/prometheus/prometheus.yml`
  (currently `host.docker.internal:8080`);
- any Grafana panel or alert whose series come from that job — including
  everything in WP-66 and WP-67.

(Note there is no application service in `docker-compose.local.yml`; the app runs
on the host, so no compose port mapping changes.)

**Done when.** `/actuator/prometheus` is unreachable from the public port **and**
Prometheus still shows the `wealthpay` target as `up`.

---
---

## WP-26 — Exception handlers leak internal detail on 5xx

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-29, WP-57

**Where:** `shared/infrastructure/web/GlobalExceptionHandler`;
`account/infrastructure/web/AccountExceptionHandler`

**Defect (verify first).** The catch-all `@ExceptionHandler(Exception.class)`
puts `e.getMessage()` into the HTTP body. For jOOQ and Postgres exceptions that
carries table names, column names, and constraint names. The
`HttpMessageNotReadableException` handler has the same issue — its message
includes Jackson class paths.

**Impact.** Schema structure disclosed to anonymous callers (WP-05).

**Fix.** For 5xx handlers, log with a generated incident ID and return only a
fixed message plus that ID.

Keep `e.getMessage()` **only** for the curated domain exceptions in
`AccountExceptionHandler` — those are deliberate business messages and are fine.

**Sequencing.** Do **WP-57 first**. WP-57 migrates the whole error surface to
`ProblemDetail`; implementing a bespoke body shape here beforehand is guaranteed
rework.

**Done when.** A forced database error returns no schema detail in the response
body, and the incident ID appears in the logs.

---
---

## WP-27 — No distributed tracing despite observation wiring

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-28, WP-29

**Where:** `pom.xml`; `src/main/resources/application.properties`

**Defect.** No tracer bridge is on the classpath — confirmed absent:
`micrometer-tracing-bridge-otel`, `micrometer-tracing-bridge-brave`,
`opentelemetry-exporter-*`, `zipkin`. Nothing.

What makes this a defect rather than a gap is that **four things are wired as if
tracing existed**:

- `management.observations.annotations.enabled=true`
- `spring.kafka.listener.observation-enabled=true`
- `@Observed(name = "account.load")` on `AccountLoader`
- `spring-modulith-observability` on the runtime classpath

Micrometer Observation with no `Tracer` bean emits **metrics only**. Every one of
those produces timers and zero spans. Nothing in the README or `docs/` says so.

**Impact.** For a system whose value proposition is an async flow across
DB → Debezium → Kafka → projector, no request can be followed through it.

**Fix.** Add `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-otlp`,
and set `management.tracing.sampling.probability`. The four integration points
above begin working immediately — the wiring is already correct, only the bridge
is missing.

**Done when.** A single request produces a connected trace across controller,
application service, and repository.

---
---

## WP-28 — Debezium CDC severs trace context

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-27

**Where:** `account/infrastructure/db/repository/OutboxRepository`;
`debezium/register-connector.sh`; `db/migration/account/` (all migrations)

**Defect.** The outbox row carries no trace column — confirmed, no
`trace_parent`, `trace_`, or `correlation` column exists in any of the 17
migrations. The connector's
`transforms.outbox.table.fields.additional.placement` forwards exactly
`event_type`, `aggregate_version`, and `occurred_at`. No trace header.

**Impact.** The write path (HTTP → command → outbox insert) and the read path
(Kafka → consumer → projection) are **two permanently disconnected trace trees**,
joined only by wall-clock correlation. When the projection lags, "which request
produced this stale row?" is unanswerable.

**Fix** — the standard outbox-tracing pattern, in order:

1. Migration adding `trace_parent TEXT` (optionally `trace_state`) to
   `account.outbox`.
2. In `OutboxRepository.publish`, inject the current context into the W3C
   `traceparent` format and write it into the row — inside the same transaction,
   which is what makes it exact rather than best-effort.
3. Add `trace_parent:header:traceparent` to the connector's
   `additional.placement` list.
4. In the consumer, extract and propagate it so the consumer span becomes a
   *link* to the producer span (a link, not a child — the consumer is not
   synchronously caused by the producer).

**Interim.** Until this lands, document the break in
`docs/adr/003-transactional-outbox-pattern-with-cdc.md`. An undocumented trace
discontinuity is worse than a documented one, because on-call assumes the
tooling is broken.

**Sequencing.** Do WP-27 first — there is no context to propagate without it.

**Done when.** A trace spans the HTTP request and the projection update.

---
---

## WP-29 — No structured logging, MDC, or correlation ID

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-26, WP-27, WP-30, WP-57

**Where:** `src/main/resources/` (no `logback-spring.xml`, no
`logging.structured.*`); `src/main/java` (no MDC usage anywhere);
`openapi/shared-api.yaml` → `ApiError`

**Defect.**

- No logging configuration at all → default plaintext console pattern, not
  machine-parseable.
- Zero MDC usage; no servlet filter establishing a request ID.
- `ApiError` carries only `status` and `message` — **no `traceId`** — so a caller
  reporting an error hands you nothing greppable.

**Fix.**

1. Enable structured output. Spring Boot 4 ships this natively:
   `logging.structured.format.console=ecs` (or add `logstash-logback-encoder`
   with a `logback-spring.xml` if more control is needed).
2. A `OncePerRequestFilter` seeding MDC with a request ID — accept an inbound
   `X-Request-Id`, else generate — plus `traceId`/`spanId` once WP-27 lands.
3. Surface the trace ID in error responses.

**Sequencing.** Do **WP-27 first** (no trace ID exists without a tracer) and
**WP-57 first** for the response-body part — WP-57 replaces `ApiError` with
`ProblemDetail`, so adding a field to `ApiError` beforehand is wasted work.

**Done when.** Logs are JSON, every line inside a request carries a correlation
ID, and that ID is returned to the caller on error.

---
---

## WP-30 — No business audit log and no actor attribution on events

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-05, WP-29, WP-116

**Where:** `account/domain/event/AccountEventMeta`;
`db/migration/account/V1__init_account_schema.sql` (`event_store` columns)

**Defect.** `AccountEventMeta` is `(eventId, accountId, occurredAt, version)` —
no `actorId`, no `correlationId`, no `causationId`. The `event_store` table has
no such columns either. Every logging call site in the codebase is technical
(exception handlers, metric failures, outbox cleanup); none records a business
action with its principal.

**Impact.** The append-only event store is an excellent audit substrate —
better than any log file, and enforced by database triggers. But with no actor
it answers "what happened" and never "who did it", which is precisely what a
financial audit requires. This gets dramatically more expensive after millions
of rows exist.

**Fix.**

1. Add `actorId`, `correlationId`, and `causationId` to `AccountEventMeta` and
   the `event_store` schema. Populate `actorId` from the authenticated principal
   once WP-05 lands.
2. Add a **separate** `AuditLogger` — distinct logger name, therefore distinct
   appender and distinct retention — emitting one structured record per
   state-changing command: principal, command, accountId, amount, outcome,
   timestamp, resulting version.

**Sequencing.** Step 1's schema change is additive and can land early; the
`actorId` value requires WP-05. Note the interaction with WP-10 — adding fields
to the event payload is exactly the schema-evolution case WP-10 exists to make
safe, so **do WP-10 first** if the fields go in the payload rather than in
dedicated columns.

**Done when.** Every state-changing command produces an audit record naming its
principal, and the event stream carries the same attribution.

---
---

## WP-31 — Outbox gauge fails into the healthy band and queries on scrape

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-16, WP-66 (the freshness gauge introduced here needs a panel)

**Where:** `account/infrastructure/db/repository/OutboxMetrics` → `fetchEstimate`

**Defect.** Two problems in one method:

1. `catch (DataAccessException e) { ...; return 0.0; }`. The
   `OutboxTableGrowing` alert fires at `> 500000`, so while the database is
   unreachable the gauge reads **0** and the alert is *guaranteed silent* —
   during exactly the incident where outbox growth matters most.
2. `Gauge.builder(...)` with a supplier means the SQL runs **synchronously on
   every Prometheus scrape**. A slow Postgres makes `/actuator/prometheus` slow,
   and a scrape timeout blinds **every** metric from that instance, not just
   this one.

**General rule.** A failed metric must not fail into the healthy band. Failing
to `0` is worse than having no metric, because it actively suppresses the alert.

**Do not use `Double.NaN` as the fix.** Verified against the
`micrometer-registry-prometheus` version this project resolves: a NaN gauge is
**still emitted** as `outbox_row_count_estimate NaN`. The series is present, so
`absent()` will not fire, and `> 500000` evaluates false — leaving the alert
exactly as silent as it is today.

**Fix.** Two changes, both needed:

1. **Move the query off the scrape path.** Mirror the sibling class
   `OutboxCleanupObserver`, which already does this correctly: `@Scheduled`
   polling into an `AtomicLong` that the gauge reads for free.
2. **Make failure detectable.** Publish a companion freshness gauge, updated
   only on a successful poll, and alert on staleness:

   ```promql
   time() - outbox_row_count_last_success_timestamp_seconds{job="wealthpay"} > 900
   ```

   Alternatively, unregister the meter on failure so the series genuinely
   disappears and `absent()` works — but the freshness gauge is simpler to
   reason about and does not churn the registry.

   **Do not copy the `OutboxCleanupStale` rule verbatim.** That rule guards
   cold start with `and max(outbox_cleanup_last_run_seconds) > 0`, which
   suppresses the alert while the gauge sits at its initial `0` — i.e. if the
   database is unreachable from process start, the alert is silent. That is the
   same fail-into-the-healthy-band pattern this item exists to remove. Instead,
   **seed the gauge with process start time** so `time() - seed` grows from
   boot and the alert fires on a cold failure.

   **Meter naming.** Micrometer only renders the `_seconds` Prometheus suffix if
   the meter name ends in a literal `.seconds` segment (as the sibling
   `outbox.cleanup.last_run.seconds` does) or the gauge declares
   `.baseUnit("seconds")`. Name it accordingly or the alert expression will not
   match.

**Done when.** With the database stopped, `/actuator/prometheus` still responds
promptly **and** a staleness alert fires within its evaluation window. Verify
by scraping the endpoint, not by reading the code.

---
---

## WP-32 — Liveness and readiness are not distinct; Kafka not health-checked

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `src/main/resources/application.properties`; no custom
`HealthIndicator` in `src/main/java`

**Defect.** `management.endpoint.health.probes.enabled` is unset, there are no
health groups, and no custom health indicator exists.

**Impact.**

1. One endpoint serves both roles. A transient database blip makes
   `/actuator/health` report DOWN → a **liveness probe kills the pod** instead of
   merely removing it from the load balancer, turning a recoverable dependency
   hiccup into a restart storm.
2. Spring Boot ships no Kafka health indicator, so the application reports **UP
   with a dead consumer** — serving indefinitely stale balances while every
   probe is green. `ConsumerBacklogNotDraining` catches it in Prometheus, but
   readiness does not, so traffic keeps routing to a broken instance.

**Fix.** `management.endpoint.health.probes.enabled=true`. Put `db` plus a custom
Kafka `HealthIndicator` (admin-client `describeCluster` with a short timeout) in
the **readiness** group; keep **liveness** to process-liveness only.

**Done when.** Stopping Kafka makes the instance not-ready but still alive, and
stopping the database does not trigger a restart loop.

---
---

## WP-33 — No rate limiting, request size limits, or CORS policy

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-05, WP-13

**Where:** `src/main/resources/application.properties`; `src/main/java` — no
`cors`, `@CrossOrigin`, `server.tomcat.*`, or `max-http-request-header` anywhere

**Defect.** All Spring defaults.

**Impact.** Unauthenticated (WP-05) plus unthrottled means `POST /accounts` is a
free unbounded write amplifier: every call writes to `event_store` **and**
`outbox`, which Debezium then streams to Kafka. An attacker fills the outbox
partition, trips `OutboxTableGrowing`, and grows the replication slot toward the
`ReplicationSlotWalRetentionHigh` disk-fill threshold. The existing alert rules
describe the resulting outage.

**Fix.** Rate limiting keyed on the authenticated principal (Bucket4j or at the
gateway); explicit `server.max-http-request-header-size` and a body cap; an
explicit CORS allowlist. The default no-CORS posture is safe today — make it
deliberate before a browser client arrives.

**Done when.** A burst of unauthenticated requests is throttled rather than
absorbed.

---
---

## WP-34 — No dependency vulnerability scanning

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-35, WP-111, WP-73

**Where:** `.github/` (contains only `workflows/ci.yml`); `pom.xml`

**Defect.** No `dependabot.yml`, no `renovate.json`, no OWASP
`dependency-check`, no Snyk, no Trivy or Grype. SonarCloud is SAST and does not
perform software-composition analysis for transitive CVEs.

**Impact.** The dependency surface is large — Spring Boot 4, Jackson 3, Confluent
8.1.1, Avro, Kafka clients, jOOQ, Debezium — with a live history of
deserialization CVEs. A vulnerable transitive dependency would go unnoticed
indefinitely. This is also the root cause of WP-35.

**Fix,** in order of value per effort:

1. `.github/dependabot.yml` with the `maven` and `github-actions` ecosystems,
   weekly. Roughly eight lines and the single largest win here.
2. `actions/dependency-review-action` on pull requests.
3. `org.owasp:dependency-check-maven` with `--failOnCVSS 7` on a **nightly**
   schedule, not per-PR — the NVD download makes it too slow for the PR path.
4. Container scanning for the custom images under `docker/`.

**Done when.** Dependabot opens PRs and a nightly CVE scan runs.

---
---

## WP-35 — Spring Boot 4.0.2 is behind and the line EOLs 2026-12-31

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-34

**Where:** `pom.xml` → parent version

**Defect.** The project is on Spring Boot **4.0.2**. The latest 4.0 patch at the
time of assessment was **4.0.7**, and the 4.0 line reaches end of life on
**2026-12-31**. Java 25 is confirmed LTS with free updates through September
2028, so the JDK is fine.

**Impact.** Five patch releases behind, on a line with limited remaining life.
Some of those patches carry security fixes.

**Fix.** Upgrade to the latest 4.0.x now, and plan the move to 4.1.x before the
4.0 EOL. **Verify the current latest at upgrade time** rather than trusting the
numbers above. Land WP-34 first so this does not recur.

**Done when.** On a supported patch release, with Dependabot keeping it current.

---
---

## WP-36 — Sonar runs but does not gate the build

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `.github/workflows/ci.yml` → `sonar` job

**Defect.** `mvn sonar:sonar` is invoked without
`-Dsonar.qualitygate.wait=true`. The scan uploads and the step exits 0
regardless of whether the quality gate passes.

**Impact.** Sonar is decoration. Nothing it finds can block a merge.

**Fix.** Append `-Dsonar.qualitygate.wait=true` to the command. One flag.

**Done when.** A deliberate quality-gate violation fails the pipeline.

---
---

## WP-37 — PITest excludes the entire `customer` bounded context

**Status:** DONE · **Confidence:** VERIFIED
**Related:** WP-38, WP-106, WP-119, WP-120, WP-121

**Where:** `pom.xml` → `pitest-maven` → `targetClasses`

**Defect.** `targetClasses` lists only `org.girardsimon.wealthpay.account.*` and
`org.girardsimon.wealthpay.shared.*`. The whole `customer` bounded context — 18
main classes and 32 tests — is invisible to mutation testing.

**Impact.** The 80% threshold is computed as if the new bounded context does not
exist, so it passes no matter how weak those tests are.

**Fix.** Add `<param>org.girardsimon.wealthpay.customer.*</param>`. One line.
**Land before the customer branch merges.**

**Done when.** The mutation report includes `customer` classes and still meets
the threshold.

**Resolved:** `309734c` — `customer.*` added to `targetClasses`. Verified by
running `mvn pitest:mutationCoverage` and parsing `target/pit-reports/mutations.xml`
**at branch tip, after `fccaa7e` added the admission types** (`309734c` itself
reported 35/38 for the BC and 195/216 overall): the `customer` bounded context now
contributes 100 mutants, 98 killed (98%), against a run total of 278 mutants and
258 killed (93%). Both surviving `customer`
mutants are in `CustomerNumber.passesLuhn` and are provably equivalent — `digit`
is always `2 × original` and therefore never exactly 9, and negating the Luhn
accumulator preserves `≡ 0 (mod 10)`. Do not re-litigate them.

**Follow-ups this fix revealed.** Two, filed separately:

- **WP-119** (P2) — `Money`'s arithmetic and comparison methods have no direct
  tests; six mutants survive, three of which allow the currency-mismatch guard to
  be deleted with a green suite. This is the substantive one.
- **WP-120** (P3, `WONTFIX`) — PITest's `FRECORD` filter hides record compact
  constructors from the gate. Investigated and **measured**: 53 hidden mutants,
  52 killed, so the hidden region is already well covered and the effect on
  `account.domain`'s score is +1.2 points, not −. Its one real finding is
  WP-99's. Recorded so the "our score is overstated" theory is not re-derived.

---
---

## WP-38 — No enforced coverage threshold

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-37, WP-119, WP-120

**Where:** `pom.xml` → `jacoco-maven-plugin`

**Defect.** JaCoCo has `prepare-agent` and `report` but **no `check` goal**. The
report exists solely to feed Sonar. Nothing fails on a coverage drop.

**Fix.** Add a `check` execution bound to `verify`, scoped to the packages that
matter — roughly 90% line and 85% branch on `**/domain/**` and
`**/application/**`, with no rule on infrastructure.

**Do not set a repository-wide number** — that produces exactly the throwaway
tests this codebase has so far avoided.

**Done when.** Deleting a domain test fails the build.

---
---

## WP-39 — DLQ path has no tests

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-02, WP-03, WP-04, WP-115

**Where:** `src/test/java/.../infrastructure/consumer/`

**Defect.** `KafkaErrorConfig` configures a `DeadLetterPublishingRecoverer`,
a backoff policy, and a non-retryable classification.
`AccountEventDeserializerTest` proves the deserializer throws exactly those
exceptions on malformed input — so poison messages *should* route to the DLT.
**Nothing asserts that they do.** The retry behaviour, the DLT topic name, the
preserved headers, and the classification are all unverified.

**Impact.** This absence is why WP-02, WP-03, and WP-115 went undetected.

**Fix.** Extend the consumer test: publish a record with a missing `eventType`
header, consume from `wealthpay.AccountEvent.DLT`, and assert the original
payload plus the `kafka_dlt-exception-fqcn` header. `@EmbeddedKafka` supports
this directly; roughly 30 lines.

**Done when.** The test exists, passes, and fails if the DLT bean is removed.

---
---

## WP-40 — outbox to Kafka is never tested end to end

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-75

**Where:** `src/test/java/` — no Kafka container test exists

**Defect.** The most load-bearing integration in the architecture (Postgres WAL
→ Debezium → `EventRouter` SMT → `wealthpay.AccountEvent`) is validated only by
hand via docker-compose and `debezium/register-connector.sh`.

**Impact.** A column rename in the outbox table, a change to the SMT
configuration, or a header-name change breaks production while `mvn verify`
stays green.

**Fix.** `testcontainers-kafka` is **already declared in `pom.xml` and unused**
(WP-75) — this is what it is for. One test with `PostgreSQLContainer`
(`wal_level=logical`), a Kafka container, and a Debezium Connect container:
insert into `outbox`, consume from the topic, assert the routed record and its
headers.

This is the highest-value test missing from the repository.

**Done when.** The test runs in CI and fails if the SMT configuration changes.

---
---

## WP-41 — event_store and outbox atomicity is untested

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `src/test/java/.../db/repository/OutboxRepositoryTest` — exactly one
test

**Defect.** `OutboxRepositoryTest` contains only
`publish_should_persist_events_in_outbox`. Nothing proves the event-store append
and the outbox insert share a transaction and roll back together — which is the
entire correctness claim of the transactional-outbox pattern and the basis of
the README's "same transaction" promise.

The behaviour is currently correct (both writes go through one Spring-managed
`DSLContext` inside `@Transactional`, and `OutboxRepository` has no own
`@Transactional` so it joins). It is simply unprotected against regression.

**Fix.** A Testcontainers test wiring the real repositories behind a
`TransactionTemplate`: force a failure after the event-store write and assert
**both** tables are empty. Add the mirror case — success populates both with
matching `event_id`.

**Done when.** Removing `@Transactional` from a command method fails the test.

---
---

## WP-42 — No read-model rebuild path, Kafka retention is 24h

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-76, WP-117

**Where:** `docker-compose.local.yml` (`KAFKA_LOG_RETENTION_HOURS: 24`);
`debezium/register-connector.sh` (`snapshot.mode: never`)

**Defect.** 24-hour Kafka retention, `snapshot.mode: never`, and 3-day outbox
partition retention together mean: if the projector is down for more than 24
hours, those events are gone from Kafka. The event store remains the source of
truth — correctly — but **no code can rebuild `account_balance_view` from it**.
There is no replay command, no admin endpoint, and no documented procedure.

**Fix.** A `ProjectionRebuilder` that streams `event_store` ordered by
`(account_id, version)` through the existing `AccountBalanceProjector`. The
projector is already idempotent (version skip-check plus a `WHERE version <`
guard on the upsert), so a rebuild is safe against a live view.

Small piece of code; converts a data-loss incident into a 20-minute operation.

**Done when.** Truncating `account_balance_view` and running the rebuilder
reproduces it exactly, covered by a test.

---
---

## WP-115 — Kafka retry backoff is unbounded

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-02, WP-03, WP-17, WP-39

**Where:** `shared/config/KafkaErrorConfig` → `errorHandler`;
`docs/adr/005-idempotent-projections-and-dlq.md`

**Defect.** The handler builds
`new ExponentialBackOff(initialIntervalMs, multiplier)`. Verified at bytecode
level against the resolved `spring-core` artifact: that two-argument constructor
leaves `maxElapsedTime = Long.MAX_VALUE` and `maxAttempts = Long.MAX_VALUE`,
with `maxInterval` capped at 30s and `jitter = 0`.

So any exception **not** in the non-retryable list retries forever at a
30-second ceiling and never reaches the recoverer.

**This is a documented decision, not an oversight.** ADR-005 explicitly chooses
unlimited retries for retriable errors to preserve event ordering, and
acknowledges that this can mask a persistent infrastructure issue. The
compensating control exists and is well designed: the
`ConsumerBacklogNotDraining` alert fires on lag > 0 with a zero consume rate.

**Why it is still listed.** Three concrete problems the ADR does not cover:

1. **It makes WP-17 unimplementable as written.** WP-17's fix specifies
   "retryable with bounded attempts" — that capability does not currently exist.
2. **The non-retryable list is narrow.** This project registers only
   `IllegalStateException` and `IllegalArgumentException`. (Spring Kafka's
   `ExceptionClassifier` already treats `DeserializationException`,
   `MessageConversionException`, `ConversionException`,
   `MethodArgumentResolutionException`, `NoSuchMethodException`, and
   `ClassCastException` as non-retryable by default — do not re-add those, and
   do not reach for `defaultFalse()` without understanding what it disables.)
   The gap is genuine programming errors: a `NullPointerException` — for example
   from a null record key reaching `UUID.fromString` in
   `AccountEventDeserializer` — is treated as retryable and loops forever.
3. **`jitter = 0`.** All three listener threads retry in lockstep during a
   database outage, producing a synchronized thundering herd on recovery.

**Fix.** Keep the ordering-preservation intent, but make the behaviour
deliberate rather than inherited from a constructor default:

```java
var backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
backOff.setMaxInterval(30_000);   // restates the default, explicitly
backOff.setJitter(500);           // default is 0 — this is the behaviour change
// Set maxAttempts only if the ordering trade-off in ADR-005 is revisited.
```

At minimum, add `jitter`, broaden the non-retryable classification to cover
genuine programming errors, and **state the unbounded choice explicitly in code
with a comment pointing at ADR-005** so it reads as intentional. Then revisit
ADR-005 alongside WP-17 to decide whether bounded attempts plus a working DLQ
are now preferable.

**Done when.** The backoff configuration is explicit, jitter is non-zero,
programming-error exceptions are classified non-retryable, and ADR-005 reflects
whatever decision is taken about bounding.

---
---

## WP-116 — No erasure strategy for PII in an immutable event store

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-30, WP-65, WP-117

**Where:** `customer/domain/model/EmailAddress`, `PersonalName`, `Nationalities`,
`Gender`, `IndividualDetails`;
`db/migration/account/V3__event_store_append_only.sql` (append-only triggers);
`docs/adr/` (no ADR covers this)

**Defect.** The in-flight `customer` bounded context models personal data —
email address, personal name, date of birth, gender, country of residence, and a
**set** of nationalities. The `account` context
stores events in an append-only table protected by `BEFORE UPDATE`/`BEFORE
DELETE` triggers, and fans them out to Kafka. There is no ADR, no design note,
and no code addressing how personal data is erased from an immutable log.

**Scope grew on the customer branch.** `86a4d3a` added `Gender` and `fccaa7e`
added `Nationalities` and `countryOfResidence`. Nationality is the field closest
to GDPR Art. 9 special-category territory (it can proxy for ethnic origin), and
it is set-valued, so an erasure design that assumes one scalar country per
customer is already wrong. Any ADR written against the original four-field list
will under-scope.

**Impact.** The immutability that makes the event store an excellent audit
substrate is directly in tension with data-subject erasure rights. Once customer
events are being written, "delete this person's data" has no answer — and Kafka
retention, outbox partitions, and any downstream projection multiply the copies.

This is the largest missing **architectural decision** in the repository, and it
is far cheaper to make now, before the customer bounded context ships its
persistence layer, than after.

**Fix.** Decide and record an ADR. Two viable options:

1. **Pseudonymisation — the recommended default *for this repository*.** Keep
   only a subject reference (`CustomerId`) in events; hold PII in a normal
   mutable table that can be deleted outright. This fits the current state
   precisely: the `account` event store carries **no PII today** (`Money`,
   `AccountId`, `TransactionId`), and the `customer` context has no persistence,
   no events, and no Kafka topic yet. It preserves the append-only trigger
   posture rather than working around it, and adds no new failure mode.
2. **Crypto-shredding** — store PII encrypted with a per-subject key held
   outside the event store; erasure destroys the key, rendering all copies
   unreadable, including those already on Kafka and in backups. The stronger
   guarantee, and the standard answer when PII genuinely must live in events.
   **Understand the cost before choosing it:** it puts a key store on the
   aggregate rehydration critical path, so a lost or corrupted key makes an
   aggregate permanently unreplayable — a new P0 failure mode, in a system whose
   entire durable state is the event log.

Choose (1) unless there is a concrete requirement that PII be carried in events.
Field-level encryption with key rotation is a variant of (2), not a third
option — and note that **rotation is not erasure**.

Whichever is chosen, the ADR must state the position for **Kafka topic
retention, outbox partitions, read-model projections, snapshots, and
backups/PITR** (see WP-117). Snapshots and backups are exactly where
pseudonymisation needs care and where crypto-shredding earns its keep.

**Done when.** An ADR exists, is referenced from `docs/context-map.md`, and the
customer persistence layer implements it. **Do not merge the customer
infrastructure layer before this decision is recorded.**

---
---

## WP-117 — No event-store backup, PITR, or restore rehearsal

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-23, WP-42, WP-116

**Where:** `scripts/`, `docker/postgres/`, `docs/` — no backup configuration,
no PITR setup, no restore runbook

**Defect.** WP-42 covers rebuilding the read model *from* the event store.
Nothing covers the event store itself being lost or corrupted.

**Impact.** In an event-sourced system the event store **is** the entirety of
durable state. Snapshots, the read model, and Kafka are all derivable; the event
store is not. There is no documented backup strategy, no point-in-time-recovery
configuration, no retention policy, and no restore drill.

A related interaction: WP-23's replication-slot WAL retention and PITR both
depend on WAL archiving policy, so they should be designed together.

**Fix.**

1. Configure continuous WAL archiving and PITR (`pgBackRest` or the managed
   equivalent), with an explicit RPO and RTO.
2. Document a restore runbook under `docs/`, following the pattern already set
   by `docs/migration/postgres-upgrade.md` — which is a good model for this.
3. **Rehearse it.** An unrehearsed restore is a hypothesis, not a capability.
   The existing `docker/pg-upgrader` rehearsal harness shows the team already
   knows how to do this well.

**Interaction with WP-116.** If crypto-shredding is chosen there, the key
material must be inside the restore scope — otherwise a successful database
restore yields unreadable ciphertext. Decide both together.

**Done when.** A documented restore drill has been executed against a copy and
the resulting event store replays to an identical aggregate state.

---
---

## WP-118 — Flyway core and Postgres plugin are on mismatched major versions

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-22, WP-59, WP-60, WP-74

**Where:** `pom.xml` → `<flyway-core.version>` property and the
`flyway-database-postgresql` dependency

**Defect.** `pom.xml` declares `<flyway-core.version>12.0.1</flyway-core.version>`,
but Spring Boot's BOM keys the managed version on `flyway.version` — so **that
override is inert**. The resolved tree is:

```
+- org.springframework.boot:spring-boot-flyway:4.0.2
|  \- org.flywaydb:flyway-core:11.14.1        <- from the Boot BOM
\- org.flywaydb:flyway-database-postgresql:12.0.1   <- explicitly pinned
```

Flyway **core 11** with the **Postgres plugin at 12**.

**Impact.** It boots today, but this is an unpinned, unintended combination on
the component that owns the database schema. Plugin/core major mismatches are
not a supported configuration, and three other items in this plan
(WP-22, WP-59, WP-60) depend on specific Flyway behaviour — script
configuration handling and transactional semantics — that differs across majors.

This is exactly the class of silent conflict WP-74's `dependencyConvergence`
rule is meant to surface.

**Fix.** Pick one major and make it explicit:

- Simplest: delete the `flyway-database-postgresql` version override and let the
  Boot BOM manage both, keeping core and plugin aligned at 11.x.
- Or override the BOM's actual property — `<flyway.version>12.0.1</flyway.version>`
  — so both move to 12 together, and verify the migrations still apply.

Then rename or remove the misleading `<flyway-core.version>` property so nobody
believes it is doing something.

**Done when.** `mvn dependency:tree` shows core and plugin on the same major, and
`maven-enforcer-plugin` (WP-74) would catch a future divergence.

---

# P2 — Correctness & operability debt

## WP-50 — Currency mismatch bypasses the domain error model

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-119

**Where:** `account/domain/model/Money` → `ensureSameCurrency`

**Sequence before WP-119**, whose cross-currency tests assert the exception type
this item changes. Note also that WP-119's mutation evidence confirms the
*reachability* half of this item — `ensureSameCurrency` is live and removable from
all three call sites with a green suite — so REPORTED now applies only to the
exception-type and metric-classification claims.

**Defect.** Every other currency violation raises
`AccountCurrencyMismatchException` → 422, classified `invariant_violation`. A
mismatch inside `Money.add`/`subtract`/`isGreaterThan` raises a bare
`IllegalArgumentException` → 400, and the metric aspect classifies it
`outcome=error` — the page-worthy bucket. **A domain rule rejection would page
someone.**

**Fix.** Throw `AccountCurrencyMismatchException` from `ensureSameCurrency`.

**Done when.** A cross-currency operation returns 422 and records
`invariant_violation`.

---
---

## WP-51 — Replay does not validate stream continuity

**Status:** TODO · **Confidence:** REPORTED

**Where:** `account/domain/model/Account` → `apply`, `rehydrateFromSnapshot`

**Defect.** `apply` assigns `this.version = event.version()` unconditionally.
`rehydrate` checks only that the first event is `AccountOpened`;
`rehydrateFromSnapshot` checks nothing — not that the snapshot's `accountId`
matches, not that the first replayed event is `snapshot.version() + 1`. A gap
yields a **silently wrong balance** that is then served on reads.

**Fix.** Assert contiguity in `apply` during replay and validate the snapshot
handoff:

```java
if (event.version() != this.version + 1) {
  throw new InvalidAccountEventStreamException(
      "Version gap: expected %d, got %d".formatted(this.version + 1, event.version()));
}
```

**Done when.** A deliberately gapped stream throws instead of producing a
balance.

---
---

## WP-52 — `updatePhase` ignores the affected row count

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-21

**Where:** `account/infrastructure/db/repository/ProcessedReservationRepository`
→ `updatePhase`

**Defect.** `.execute()` returns the update count; it is discarded. If the row is
absent the update is a no-op, the method returns normally, and the service
reports `CAPTURED` while the table still says `RESERVED`. That drift then feeds
the idempotency check in WP-21.

**Fix.** `if (updated != 1) throw new ReservationStoreInconsistencyException(...)`.

**Sequencing.** If WP-21 lands first, `processed_reservations` becomes a pure
lookup index and this check moves or disappears — coordinate the two.

**Done when.** A missing-row update raises instead of silently succeeding.

---
---

## WP-53 — Event-store load methods are unbounded

**Status:** TODO · **Confidence:** REPORTED

**Where:** `account/application/AccountEventStore`;
`account/infrastructure/db/repository/AccountEventRepository`

**Defect.** `loadEvents` materializes the full stream into a `List`. Snapshots
bound this in practice, but the fallback path — snapshot missing, or the
deserializer returning empty — replays everything into heap with no
backpressure. Also `loadEvents(id)` is exactly `loadEventsAfterVersion(id, 0)`:
two port methods where one suffices, which also means two things to mock in
every test.

**Fix.** Collapse to a single `loadEventsAfterVersion`; add a batched or streamed
variant (or a hard cap that throws) for the no-snapshot path.

**Done when.** The port has one load method and the no-snapshot path is bounded.

---
---

## WP-54 — `DataIntegrityViolationException` conflates distinct constraints

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-98

**Where:** `account/infrastructure/db/repository/AccountEventRepository`

**Defect.** The catch translates *any* integrity violation to
`OptimisticLockingFailureException` → 409 "Concurrent modification". But
`event_store` has two unique constraints: `uq_event_store_account_version`
(genuine concurrency) and `idx_event_store_event_id` (an event-ID collision,
i.e. an ID-generator bug). The latter is misreported as a conflict, retried by
the client, and never diagnosed.

**Fix.** Inspect the SQLState and constraint name; translate only the version
constraint and let an event-ID collision surface as an error.

**Done when.** An injected event-ID collision produces a 500, not a 409.

---
---

## WP-55 — `reserveFunds` duplicates `processTransaction`

**Status:** TODO · **Confidence:** REPORTED

**Where:** `account/application/AccountApplicationService`

**Defect.** The same six steps (register → check `NO_EFFECT` → load → handle →
compute prior version → save) are written twice. The only differences are
minting a `reservationId` and the extra store registration. Any fix to WP-01 or
WP-19 must now be applied in both copies.

**Fix.** Generalize `processTransaction` with a handler function plus
`onNoEffect` and `afterSave` hooks, or extract the shared prologue.

**Done when.** One code path serves both.

---
---

## WP-56 — `Customer.status` is unreachable dead state

**Status:** DONE · **Confidence:** VERIFIED

**Where:** `customer/domain/model/Customer`

**Defect.** `status` is the only non-final field, set to `ONBOARDING` in
`register`, with no transition method. It can never become `ACTIVE`. Today
`Customer` is a validated data holder with one derived accessor — anemic, in
contrast to `Account`. Known and in-progress rather than an oversight, but
recorded so it does not ship in this shape.

**Fix.** `activate()` — `ONBOARDING → ACTIVE`, idempotent, rejecting from any
terminal state.

**Done when.** A domain test drives the transition.

**Resolved:** `493acf4` — `Customer.activate(Instant)` implements
`ONBOARDING → ACTIVE`, idempotent (`case ACTIVE -> false`), as an exhaustive
switch *expression* so that adding a state breaks the build here rather than
silently reactivating a blocked customer. `status` is also no longer the only
non-final field: `activatedAt` joined it, nullable until activation, exposed as
`Optional<Instant>`. `CustomerActivationTest` drove the transition across **5**
tests at that commit.

**Extended by `fccaa7e`**, which is where the rest of today's behaviour lives —
do not attribute it to `493acf4`. It added `registeredAt` (there is no such field
at `493acf4`, and `register` takes no instant there), the invariant
`activatedAt >= registeredAt`, and 3 further tests, taking
`CustomerActivationTest` to 8.

That ordering guard sits **inside** the ONBOARDING arm rather than before the
switch: guarding before it would turn an idempotent retry under clock skew into a
hard error.

Confidence raised REPORTED → VERIFIED on the tests plus the mutation report, in
which `Customer` contributes 24 mutants with no survivors — **measured at branch
tip**, not at `493acf4`.

Suspend / reinstate / close remain deliberately deferred — they need a reason
axis on the transition record and the cross-context "does this customer still
have open accounts?" invariant. `docs/context-map.md` records that this is a
prerequisite for Seam B, not part of it.

---
---

## WP-57 — Error responses are not RFC 7807

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-26, WP-29, WP-58

**Where:** `openapi/shared-api.yaml` → `ApiError`, `ValidationError`

**Defect.** The error surface uses a bespoke `ApiErrorDto` rather than Spring
Boot 4's built-in `ProblemDetail`. No `application/problem+json`, no `type` URI,
no `instance`, and no correlation ID.

**Fix.** Migrate to `ProblemDetail` / `ErrorResponseException`; Spring Boot 4
supports it natively and springdoc documents it.

**Sequencing.** **Do this before WP-26 and WP-29.** Both of those modify the
error response body; doing either first is rework.

**Done when.** Errors are served as `application/problem+json` with a trace ID.

---
---

## WP-58 — OpenAPI documents no error responses

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-57

**Where:** `openapi/account/account-api.yaml` — every operation declares only
`200`/`201`

**Defect.** The handlers produce 400, 404, 409, 422, and 500. None appear in the
spec. Generated clients get no error model, contract tests cannot assert failure
paths, and the spec actively misrepresents the API.

**Fix.** Add a shared `responses:` block with `$ref`s for 400/404/409/422/500 on
every operation. Coordinate with WP-57 so the referenced schema is the final one.

**Done when.** The spec matches what the handlers actually return.

---
---

## WP-59 — Duplicate index on `event_store`

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-60, WP-118

**Where:** `V1__init_account_schema.sql`; `V2__event_store_version_consistency.sql`

**Defect.** `idx_event_store_account_id_version` and the
`uq_event_store_account_version` UNIQUE constraint cover the identical column
tuple. The highest-volume table in the system maintains two structurally
identical indexes on every append — pure write amplification.

**Fix.** New migration:
`DROP INDEX CONCURRENTLY account.idx_event_store_account_id_version`. The unique
index fully serves both `loadEventsAfterVersion` and the `max(version)` probe.

**Implementation note — this will fail without it.** Flyway runs migrations
inside a transaction by default (`executeInTransaction` defaults to `true`), and
`DROP INDEX CONCURRENTLY` cannot run in a transaction block — PostgreSQL raises
`25001`. Add a sidecar configuration file next to the migration:

```
# V<N>__drop_duplicate_event_store_index.sql.conf
executeInTransaction=false
```

**Done when.** One index remains and query plans are unchanged.

---
---

## WP-60 — Unused indexes on `outbox` and `processed_reservations`

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-61, WP-59, WP-118

**Where:** `V13__partition_outbox.sql`; `V9__processed_reservations.sql`

**Defect.** Nothing reads the outbox — Debezium consumes the WAL, not the table
— so `outbox_aggregate_order_idx` is maintained on every insert for zero read
benefit. The `UNIQUE (event_id, occurred_at)` constraint has defensible value as
a duplicate guard; the ordering index does not.
`processed_reservations_occurred_at_idx` is likewise unused — queries go by
`(account_id, reservation_id)` and `(account_id, transaction_id)`, both already
covered.

**Fix.** Confirm via `pg_stat_user_indexes` under real traffic, then drop both —
but **the two indexes need opposite treatment**:

- `outbox_aggregate_order_idx` is created on the **partitioned parent**
  (`V13__partition_outbox.sql`). PostgreSQL rejects
  `DROP INDEX CONCURRENTLY` on a partitioned index, and the per-partition leaves
  cannot be dropped individually either (they are required by the parent). The
  only path is a plain `DROP INDEX` on the parent, which takes `ACCESS
  EXCLUSIVE` on the parent **and every partition** — so use
  `SET LOCAL lock_timeout`, and **not** the `.conf` sidecar from WP-59.
- `processed_reservations_occurred_at_idx` is on a plain table, so
  `DROP INDEX CONCURRENTLY` is correct there, with the WP-59 `.conf` sidecar.

**Sequencing.** **Do WP-61 first.** If WP-61 adds a retention job that prunes by
`occurred_at`, `processed_reservations_occurred_at_idx` becomes useful and must
not be dropped.

**Done when.** Verified unused, then dropped.

---
---

## WP-61 — Idempotency tables grow without bound

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-60

**Where:** `V8__processed_transactions.sql`; `V9__processed_reservations.sql`

**Defect.** Both are written on every command and never pruned. No cleanup job,
no partitioning, no TTL — only `manage_outbox_partitions` exists. At volume these
become the largest tables in the database, degrading the idempotency check on
the critical write path.

**Fix.** Choose an idempotency window (24–48h is typical; it only needs to
outlive client retry behaviour) and either partition by `occurred_at` like the
outbox, or add a batched `DELETE` to the existing pg_cron job.

**Done when.** A retention job runs and table size is bounded.

---
---

## WP-62 — Partition drop has no `lock_timeout`

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-16, WP-63

**Where:** `V16__outbox_cleanup_observability.sql` → partition drop

**Defect.** Dropping a partition acquires `ACCESS EXCLUSIVE` on the partitioned
parent. Because Postgres lock requests queue, a conflicting lock makes the DROP
wait and every subsequent outbox `INSERT` block behind it. Harmless at 03:00; not
during an incident-driven manual run.

**Fix.** `SET LOCAL lock_timeout = '5s';` at the top of the function body. A
skipped drop is logged as a failure and retried tomorrow; a lock pile-up is a
write outage.

**Coupling with WP-16.** A contended drop now raises `55P03`, which reaches the
function-level `EXCEPTION WHEN OTHERS` and rolls back the **partition creates**
too, not just the drop — and those creates are WP-16's precondition. Either
handle `lock_not_available` locally around the drop, or accept that a contended
night skips creation as well and rely on the DEFAULT partition to absorb it.

**Done when.** The timeout is set and a contended run degrades gracefully.

---
---

## WP-63 — No CHECK constraints on the read model

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-62

**Where:** `V1__init_account_schema.sql` → `account_balance_view`

**Defect.** No CHECK constraints: `balance` and `reserved` may go negative,
`status` accepts any 20-char string, `currency` any 3-char string. All invariants
live in the aggregate. Foreign keys are genuinely unavailable here — there is no
`accounts` table by design — and that trade-off is reasonable, but cheap CHECKs
would catch projector bugs at the point of corruption rather than when a
customer reads a negative balance.

**Fix.** Add the constraints `NOT VALID` first, then validate separately, so the
`ACCESS EXCLUSIVE` lock is held only briefly and the full-table scan does not
block writes — the same hazard WP-62 describes:

```sql
ALTER TABLE account.account_balance_view
  ADD CONSTRAINT chk_abv_reserved_non_negative CHECK (reserved >= 0) NOT VALID,
  ADD CONSTRAINT chk_abv_status   CHECK (status IN ('OPENED','CLOSED')) NOT VALID,
  ADD CONSTRAINT chk_abv_currency CHECK (currency ~ '^[A-Z]{3}$') NOT VALID;

-- separate migration / separate transaction
ALTER TABLE account.account_balance_view VALIDATE CONSTRAINT chk_abv_reserved_non_negative;
-- ...and the other two
```

Deliberately omit `balance >= 0` — overdraft policy is a domain decision, not a
storage one.

**Done when.** Constraints exist, are validated, and the projector tests pass.

---
---

## WP-64 — Debezium credentials hardcoded in a tracked script

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `debezium/register-connector.sh`

**Defect.** `"database.user": "user"`, `"database.password": "password"` inline
in a tracked script, with no production variant and no externalization.

Notably, `docker/sql-exporter/sql_exporter.yml` and the Postgres bootstrap SQL
both carry explicit "LOCAL-ONLY CREDENTIALS — do NOT copy this file verbatim"
warnings. This script has none, and it is the file most likely to be adapted for
a real environment. The Grafana block in `docker-compose.local.yml` lacks the
banner too.

**Fix.** Read `${DB_USER}`/`${DB_PASSWORD}` from the environment; add the same
warning banner the sibling files carry; use Connect `config.providers` for real
environments.

**Done when.** No credentials are literal in the script.

---
---

## WP-65 — Financial payloads written unmasked to technical logs

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-116

**Where:** `account/infrastructure/consumer/AccountOutboxConsumer`;
`shared/infrastructure/web/GlobalExceptionHandler`

**Defect.** The consumer logs the **entire event JSON payload** — amounts and
currency included — on the unsupported-event path. The validation handler logs
the full `MethodArgumentNotValidException`, whose binding result carries the
caller's raw `rejectedValue`. No masking exists anywhere.

**Fix.** Log identifiers (`eventId`, `accountId`, `offset`), never payloads. If
the payload is needed for triage it is already on the DLQ topic — log the
coordinates to find it, not the content.

**Done when.** No monetary values appear in technical logs.

---
---

## WP-66 — Three outbox alerts have no dashboard panel

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-31

**Where:** `docker/grafana/dashboards/` (all five dashboards);
`docker/prometheus/rules/wealthpay.yml`

**Defect.** `outbox_row_count_estimate`, `outbox_cleanup_last_status`, and
`outbox_cleanup_last_run_seconds` are alerted on but appear on **zero**
dashboards. On-call is paged for `OutboxCleanupFailed` at 3am with nowhere to
look. For an outbox/CDC-centric system this is the most surprising omission in
the observability set.

**Fix.** Add an outbox row to the application dashboard covering all three, plus
the freshness gauge introduced by WP-31.

**Done when.** Every alerting metric has a panel.

---
---

## WP-67 — Command dashboard surfaces 1 of 6 outcomes

**Status:** TODO · **Confidence:** REPORTED

**Where:** `docker/grafana/dashboards/account-service.json`;
`docker/grafana/dashboards/db-server-health.json`

**Defect.** The metric aspect emits six outcomes; the dashboard surfaces one
(`concurrency_conflict`, as a ratio). **`invariant_violation` and `error` are
invisible** — the former is the business-rule rejection rate for a bank account,
the latter the page-worthy bucket the aspect goes to deliberate trouble to
isolate. There is no throughput panel at all, so the **R and E of RED are both
missing**.

Compounding: the DB dashboard's text panel instructs the operator to
"cross-reference with the `outcome="error"` tag" — pointing at a panel that does
not exist.

**Fix.** Add `sum by (command, outcome) (rate(...))` throughput and error-rate
panels; fix the dangling cross-reference.

**Done when.** All six outcomes are visible and the cross-reference resolves.

---
---

## WP-68 — Alerts and dashboards are not linked

**Status:** TODO · **Confidence:** REPORTED

**Where:** `docker/grafana/dashboards/` (all five dashboards);
`docker/grafana/grafana.yml` and `docker/grafana/dashboards.yml` (provisioning —
compose mounts these into `/etc/grafana/provisioning/` in the container)

**Defect.** Across all five dashboards: no `annotations` block, no dashboard
`links`, no `alertlist` panel, no `ALERTS{}` overlay. The Alertmanager datasource
is provisioned and used by nothing. Thirty-plus rules with zero visual
correlation, and no `dashboardUId` or `runbook_url` annotation on the rules side.

Related hygiene in the same files: zero thresholds on any app-dashboard panel
despite the rules encoding exact numbers; `$__rate_interval` never used (all
ranges hardcoded `[5m]`, so rates degrade on zoom-out); no datasource `uid:`
declared, so dashboards pin to the datasource *name* and break on any
differently-named install; `allowUiUpdates` omitted (defaults false), so the UI
invites edits it then silently discards.

**Fix.** Add `ALERTS{}` annotations and dashboard links; add `runbook_url` and
`dashboardUId` to the rules; set thresholds from the alert values; switch to
`$__rate_interval`; declare datasource UIDs.

**Done when.** A firing alert is visible on the relevant dashboard.

---
---

## WP-69 — Domain records are mocked in controller tests

**Status:** TODO · **Confidence:** REPORTED

**Where:** `AccountControllerTest`, `AccountReservationControllerTest`,
`AccountTransactionControllerTest`, `AccountReadServiceTest`

**Defect.** Roughly 11 `mock(...)` calls target Java **records** — domain
commands and application responses. Mocking a record requires the inline mock
maker, produces accessors returning `null`, and couples the test to Mockito's
ability to subclass a final type. They serve as opaque wiring tokens, so there is
no correctness bug — but they are trivially constructible and the mock buys
nothing.

**Fix.** Replace with real constructions, e.g.
`new OpenAccount(SupportedCurrency.USD, Money.of(...))`. Two lines each, and the
test then reads as a real scenario.

**Done when.** No domain type is mocked anywhere.

---
---

## WP-70 — No test data builders; fixtures duplicated

**Status:** TODO · **Confidence:** REPORTED

**Where:** `AccountApplicationServiceTest`

**Defect.** The identical `AccountOpened + FundsReserved` fixture is repeated
roughly seven times across the reservation tests — about 85 lines of duplication
that obscures what each test actually varies.

**Fix.** An `AccountFixtures.openedWithReservation(accountId, reservationId, amount)`
helper.

**Done when.** Each reservation test states only its own variable.

---
---

## WP-71 — Testcontainers restarts Postgres per test class

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-112

**Where:** `src/test/java/.../db/repository/AbstractContainerTest`

**Defect.** Ten classes extend it. JUnit's Testcontainers extension is per-class,
so the inherited `static @Container` starts in `beforeAll` and stops in
`afterAll` of *each* subclass — roughly ten container startups plus ten full
Flyway runs per build. No `.testcontainers.properties`, no `withReuse(true)`, no
singleton.

The class itself is **not** the "one giant base integration test" anti-pattern —
it holds only the container and datasource properties, and each test declares its
own imports. That part is right.

**Fix.** Use the Testcontainers singleton pattern (static initializer, no
`@Testcontainers`/`@Container`), or `withReuse(true)` plus
`testcontainers.reuse.enable=true`. Also parameterize the raw
`PostgreSQLContainer` type.

**Done when.** One container start per build.

---
---

## WP-72 — CI lacks permissions, timeout, and concurrency controls

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `.github/workflows/ci.yml`

**Defect.** No `permissions:` block (jobs run with the repository-default
`GITHUB_TOKEN` scope), no `timeout-minutes` (a hung Testcontainers start burns
six hours of runner time), no `concurrency` group (every push to an open PR
queues a full duplicate run). The artifact upload has no `if: always()`, so
`target/surefire-reports/` is lost exactly when tests fail — and it uploads the
entire `target/`. The two jobs also cache `~/.m2/repository` twice, via both
`actions/cache` and `setup-java`'s `cache: maven`.

**Fix.**

```yaml
permissions:
  contents: read
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

plus `timeout-minutes: 30` on both jobs, `if: always()` on the upload with a
narrowed `path:`, and removal of the redundant `actions/cache` steps.

**Done when.** All four are in place and the pipeline is faster.

---
---

## WP-73 — GitHub Actions pinned to mutable tags

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-34

**Where:** `.github/workflows/ci.yml`

**Defect.** All actions use mutable major tags (`@v5`, `@v6`, `@v8`). A
compromised tag re-point is the standard Actions supply-chain attack.

**Fix.** Pin to full commit SHAs with a version comment. Dependabot's
`github-actions` ecosystem (WP-34) keeps them current.

**Done when.** No mutable tag remains.

---
---

## WP-74 — `maven-enforcer-plugin` absent

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-118

**Where:** `pom.xml`

**Defect.** No `requireMavenVersion`, `requireJavaVersion`,
`dependencyConvergence`, or `bannedDependencies`. With Spring Boot 4, Confluent
8.1.1, jOOQ, and Debezium in one tree, transitive conflicts (Jackson, Guava,
Netty, snappy) are near-certain and currently silent.

**Fix.**

```xml
<rules>
  <requireMavenVersion><version>[3.9.11,)</version></requireMavenVersion>
  <requireJavaVersion><version>[25,)</version></requireJavaVersion>
  <dependencyConvergence/>
  <banDuplicatePomDependencyVersions/>
</rules>
```

bound to `validate`. Expect `dependencyConvergence` to fail loudly the first
time — that is the point.

**Done when.** The build enforces convergence and passes.

---
---

## WP-75 — Unused declared dependencies

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-05, WP-40

**Where:** `pom.xml`

**Defect.**

- `org.testcontainers:testcontainers-kafka` — zero usage. **Do not delete**; use
  it for WP-40.
- `spring-security-test` — zero usage, and no Spring Security in `src/main`.
  Delete, or keep as part of WP-05.

**Fix.** Resolve each per the note above.

**Done when.** Both are either used or removed.

---
---

## WP-76 — Snapshot restore and projection replay untested end to end

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-42

**Where:** `src/test/java/`

**Defect.** Snapshot handling is tested in pieces — loader branch logic with
mocks, repository persist/load/version-guard, and the replay-equivalence
invariant in the aggregate — but never as a chain: write a snapshot through the
real serializer to Postgres, read it back through the real deserializer, replay
subsequent events, and assert the aggregate matches full replay. Separately, no
test truncates the read model and replays to reconstruct it (see WP-42).

**Fix.** One integration test per chain.

**Done when.** Both chains are covered.

---
---

## WP-77 — Documented "snapshot never blocks" behaviour is untested

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-01

**Where:** `AccountApplicationServiceTest`

**Defect.** `CLAUDE.md` states snapshot failures never block the critical path.
No test makes `saveSnapshot` throw. A one-line `doThrow(...)` test pins the
documented guarantee — and the database-level variant is what proves WP-01 is
fixed.

**Fix.** Add both the unit-level (mocked store throws) and database-level
(genuine SQL failure inside the transaction) cases.

**Done when.** Both cases are covered.

---
---

## WP-78 — Builds are not reproducible

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `pom.xml`

**Defect.** No `<project.build.outputTimestamp>`, so JAR entry timestamps vary
per build and no two builds are byte-identical.

**Fix.** Add
`<project.build.outputTimestamp>2026-01-01T00:00:00Z</project.build.outputTimestamp>`.

**Done when.** Two clean builds produce identical artifacts.

---
---

## WP-79 — Kafka listener latency has no percentiles

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `src/main/resources/application.properties`;
`docker/grafana/dashboards/kafka-consumer.json`

**Defect.** Five metrics enable `percentiles-histogram`;
`spring.kafka.listener` does not, so no `_bucket` series exist and the dashboard
is forced to use `avg` of `_sum`/`_count` plus a decaying `_max`. Also missing:
any consumer error/retry/DLQ panel, though
`spring_kafka_listener_seconds_count{exception!="none"}` is free from the same
timer.

**Fix.** Add
`management.metrics.distribution.percentiles-histogram.spring.kafka.listener=true`,
convert the panel to `histogram_quantile`, and add an error panel.

**Done when.** p95/p99 listener latency is charted.

---

## WP-119 — `Money`'s arithmetic and comparisons have no direct tests

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-20, WP-37, WP-38, WP-50, WP-106, WP-120

**Where:** `account.domain.model.Money`; `MoneyTest`.

**🔴 Sequence after WP-50.** WP-50 changes `ensureSameCurrency` to throw
`AccountCurrencyMismatchException` instead of `IllegalArgumentException`. The
cross-currency tests prescribed below assert that exception type, so writing them
first means WP-50 breaks three fresh tests. Either do WP-50 first, or assert the
domain exception from the outset and land them together.

**Defect.** `MoneyTest` contains three methods, all covering construction, null
rejection and scale normalization. **Nothing calls `add`, `subtract`,
`isGreaterThan`, `isZero`, `isNegativeOrZero` or `isStrictlyNegative` directly.**
They are exercised only transitively through the `Account` command tests, which
assert on aggregate outcomes and never pin the value object's own boundaries.

Six mutants survive as a result (from `mvn pitest:mutationCoverage`):

```
Money.add               removed call to ensureSameCurrency   SURVIVED
Money.subtract          removed call to ensureSameCurrency   SURVIVED
Money.isGreaterThan     removed call to ensureSameCurrency   SURVIVED
Money.isGreaterThan     changed conditional boundary         SURVIVED   (> 0 → >= 0)
Money.isNegativeOrZero  changed conditional boundary         SURVIVED   (<= 0 → < 0)
Money.isZero            replaced boolean return with true    SURVIVED
```

**Impact.** Read as behaviour: **the currency-mismatch guard can be deleted from
`add`, `subtract` and `isGreaterThan` and the suite stays green.** Each survivor
sits under a live guard:

- `isNegativeOrZero` → `Account`'s non-positive transaction-amount guard.
- `isGreaterThan` → `Account`'s available-balance checks on debit and reserve.
- `isZero` → `Account`'s "account is empty" precondition on close.
- `isStrictlyNegative` → `Account`'s negative-initial-balance guard on open. Its
  mutants are killed transitively, so it shows no survivor — but it is as
  untested directly as the other five and belongs in the same fix.

This is a **test gap, not a production defect**: the guards are correct today and
the aggregate-level tests do exercise the happy paths. The risk is that a future
edit to `Money` silently removes a guard with nothing failing.

**Fix.** Write direct `MoneyTest` cases for the six methods above: cross-currency
rejection on `add`/`subtract`/`isGreaterThan`, and the zero/equality boundaries on
`isGreaterThan`, `isZero`, `isNegativeOrZero` and `isStrictlyNegative`.

**Not in scope.** Refactoring `Money`'s compact constructor to expose it to the
mutation gate — see WP-120 for why that buys almost nothing. The rounding mode is
already pinned by `should_normalize_amount_according_to_currency_fraction_digits`
(`10.505 EUR → 10.50`, which `HALF_UP` would round to `10.51`), and the null guard
by `check_money_consistency`. Both are covered by tests even though the gate
cannot see them.

**Done when.** All six survivors above are killed and each of the six methods has
a test that names it.

---

---

# P3 — Hygiene & polish

## WP-90 — `ReservationId` is UUIDv7 but externally exposed

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `account/infrastructure/id/RandomReservationIdGenerator`;
`RandomAccountIdGenerator` (Javadoc)

**Defect.** `RandomAccountIdGenerator` makes a deliberate, well-reasoned choice:
UUIDv4 for `AccountId` because v7's embedded timestamp "would leak
account-creation time". Its Javadoc then asserts `ReservationId` is "**never
exposed externally**" — but it is, in
`/accounts/{id}/reservations/{reservationId}/capture` and `/cancel`, and in the
reserve response. The generator uses `getTimeOrderedEpoch()` (v7).

Small risk; the point is that a documented invariant is factually false, which is
how good threat models decay.

**Fix.** Either switch `ReservationId` to v4, or correct the Javadoc to
acknowledge the exposure and accept it explicitly.

**Done when.** Code and documented threat model agree.

---
---

## WP-91 — ADR-008 "SLOs" are cause-based thresholds

**Status:** TODO · **Confidence:** REPORTED

**Where:** `docs/adr/008-db-observability-slos.md`

**Defect.** The document is excellent at what it does — threshold methodology,
measured baselines, a re-baseline trigger. But every entry in its SLI inventory
is a **cause** on the database: cache-hit ratio, checkpoint ratio, connection
count, WAL retention. There is no user-facing availability or latency objective,
no error budget, and no burn-rate alerting. The symptom-level alerts that would
back an SLO live in a different rules file and are tied to no budget.

**Fix.** Define two or three real SLOs with error budgets and fast/slow burn-rate
alerts. Keep the existing cause-based alerts as ticket-severity diagnostics, not
pages.

**Done when.** At least one user-facing SLO has a burn-rate alert.

---
---

## WP-92 — `last_updated_at` never refreshed on projection update

**Status:** TODO · **Confidence:** REPORTED

**Where:** `account/infrastructure/db/repository/AccountBalanceReadModel`

**Defect.** The upsert sets only the six business columns. The column has
`DEFAULT NOW()`, so it is correct on insert and frozen thereafter — read-model
staleness cannot be measured from the data.

**Fix.** Set it explicitly in the upsert, or use a trigger.

**Done when.** The column advances on every projection update.

---
---

## WP-93 — Read-side query lacks `@Transactional(readOnly = true)`

**Status:** TODO · **Confidence:** REPORTED

**Where:** `account/infrastructure/db/repository/AccountBalanceReadModel` →
balance read

**Defect.** `CLAUDE.md` states read-side repositories carry it; this one does
not.

**Fix.** Add the annotation.

**Done when.** The convention holds repository-wide.

---
---

## WP-94 — `assert` used where assertions are disabled

**Status:** TODO · **Confidence:** REPORTED

**Where:** `account/infrastructure/db/repository/OutboxCleanupObserver`

**Defect.** `assert row != null` is a no-op unless `-ea` is passed. Harmless
here (a scalar subquery always returns a row) but misleading as documentation.

**Fix.** Use an explicit check or replace with a comment.

**Done when.** No `assert` remains in main sources.

---
---

## WP-95 — One migration creates an unqualified table

**Status:** TODO · **Confidence:** REPORTED

**Where:** `V5__outbox_schema.sql`

**Defect.** Creates an unqualified `outbox`, relying on
`spring.flyway.default-schema` to set the search path. Every other migration is
schema-qualified. Fragile if the Flyway configuration changes.

**Fix.** Migrations are immutable — do **not** edit V5. Fix forward with a
comment or a guard, and note the dependency on `default-schema`.

**Done when.** The implicit dependency is documented.

---
---

## WP-96 — Append-only trigger asymmetry is undocumented

**Status:** TODO · **Confidence:** REPORTED

**Where:** `V3__event_store_append_only.sql`; `V13__partition_outbox.sql`

**Defect.** `event_store` blocks UPDATE and DELETE; `outbox` blocks only UPDATE.
This is intentional — partition management needs DELETE — but reads as an
oversight.

**Fix.** A comment in the migration or a note in
`docs/adr/007-outbox-lifecycle-management.md`.

**Done when.** The asymmetry is explained where a reader will find it.

---
---

## WP-97 — Consumer `isolation.level` left implicit

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `src/main/resources/application.properties`

**Defect.** Defaults to `read_uncommitted`. Correct for a non-transactional
Debezium producer, but implicit.

**Fix.** Pin it explicitly so a future move to transactional producers does not
silently change semantics.

**Done when.** The property is set with a brief comment.

---
---

## WP-98 — Redundant `SELECT max(version)` on every append

**Status:** TODO · **Confidence:** REPORTED
**Related:** WP-54

**Where:** `account/infrastructure/db/repository/AccountEventRepository`

**Defect.** The `UNIQUE (account_id, version)` constraint alone provides the
concurrency guarantee, and the `DataIntegrityViolationException` catch already
translates it. The pre-check adds a round-trip per write for a friendlier error
message.

**Fix.** Measure first, then consider removing. Coordinate with WP-54, which
changes how the constraint violation is interpreted.

**Done when.** The round-trip is removed or its cost is documented as accepted.

---
---

## WP-99 — `AccountEventMeta` admits `version == 0`

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-120

**Where:** `account/domain/event/AccountEventMeta`

**Defect.** Validates `version < 0L`, but no event can legitimately have version
0 — streams start at 1.

**Fix.** Tighten to `version < 1L`.

**Done when.** A version-0 event is rejected by a unit test.

**Note.** This boundary is also the sole surviving mutant found by the WP-120
investigation (`version < 0L` → `<= 0L`, unkilled). Fixing it here kills that
mutant; WP-120 is `WONTFIX` precisely because this item already owns the work.
Beware the inverted reading — the mutation report shows only that the boundary is
unexercised, not which direction is correct.

---
---

## WP-100 — `Account.toSnapshot` is a static taking an `Account`

**Status:** TODO · **Confidence:** REPORTED

**Where:** `account/domain/model/Account`

**Defect.** Should be an instance method; the static form reads like an external
mapper reaching into private state.

**Fix.** Convert to `account.toSnapshot()`.

**Done when.** Call sites use the instance form.

---
---

## WP-101 — `AccountSnapshot` NPEs on null reservations

**Status:** TODO · **Confidence:** REPORTED

**Where:** `account/domain/model/AccountSnapshot`

**Defect.** Calls `Map.copyOf` with no null guard, unlike every sibling record
which throws a described `IllegalArgumentException`.

**Fix.** Add the guard, matching the sibling convention.

**Done when.** A null reservations map produces a described exception.

---
---

## WP-102 — `Account` has no identity-based `equals`/`hashCode`

**Status:** TODO · **Confidence:** REPORTED

**Where:** `account/domain/model/Account`

**Defect.** Standard for a DDD entity. Harmless today (never used in
collections), a trap later.

**Fix.** Implement both on `AccountId`.

**Done when.** Two `Account` instances with the same ID compare equal.

---
---

## WP-103 — `AccountEventPublisher` is misnamed

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `account/application/AccountEventPublisher`

**Defect.** It writes the outbox inside the command transaction. The name implies
broker publication and invites someone to add a real Kafka send behind it,
breaking atomicity.

**Fix.** Rename to `AccountEventOutbox`, which states the contract.

**Done when.** The port name matches its behaviour.

---
---

## WP-104 — `ReservationOutcome` does not defensively copy

**Status:** TODO · **Confidence:** REPORTED

**Where:** `account/domain/model/ReservationOutcome`

**Defect.** No `List.copyOf(events)` in the compact constructor, unlike the
discipline already applied in `AccountSnapshot`.

**Fix.** Add the defensive copy.

**Done when.** The record is immutable against caller mutation.

---
---

## WP-105 — Customer BC lacks an ID-generator port

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `customer/domain/model/`

**Defect.** `Account` defines three generator ports in `domain/model` with
infrastructure implementations. `CustomerId` has none, so ID minting will land
wherever the use case is written.

**Fix.** Mirror the account convention when the application layer arrives. The
customer-number generator must never mint an all-zero number — Luhn-valid but
invalid issuance.

**Done when.** A `CustomerIdGenerator` port exists in the domain with a Spring
implementation in infrastructure.

**Partial progress (`fccaa7e`).** `CustomerNumberGenerator` now exists as a
domain-defined port — interface only, no infrastructure implementation, since
minting needs a database sequence. `CustomerIdGenerator` is still absent, so this
item stays `TODO`; the all-zero-number caveat above now attaches to the existing
port rather than a hypothetical one.

---
---

## WP-106 — Documentation drift in README and CLAUDE.md

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-05, WP-37, WP-119, WP-120, WP-121

**Where:** `README.md`; `CLAUDE.md`

**Defect.**

`README.md`:

- Claims "Google Java Format 1.33.0"; `pom.xml` pins **1.34.1**.
- No mention of the `customer` bounded context or `docs/context-map.md`.
- Credits only Spring Modulith for architecture, never the ArchUnit rules — the
  more substantial half. Gatling is absent from the testing section.
- States the PITest threshold without noting what it does and does not measure.
  Every bounded context is now a target (WP-37, done); record compact
  constructors remain invisible to the gate, though measurement shows that
  region is already well covered (WP-120).
- No CI section, so a reader cannot tell what gates a merge.
- Describes a "banking-grade account domain" with no caveat that authn/authz is
  absent (WP-05). **If auth is deliberately out of scope, say so explicitly** —
  a 30-second fix that removes the only misleading claim in otherwise exemplary
  documentation.

`CLAUDE.md`:

- States "16 rules" for `HexagonalArchitectureTest`; the class declares **17**
  `@ArchTest` methods. This file is what agents read first, so its drift costs
  more than the README's.
- Names the Modulith test `account.ArchitectureTests`. `86a4d3a` moved it to
  `org.girardsimon.wealthpay.architecture.ArchitectureTests` — it is no longer
  inside a bounded context, which is the point of the move.
- Claims "A new BC arrives with full intra-BC layer enforcement on day one — no
  manual rule registration required". **No longer true.** `86a4d3a` added
  `INCREMENTAL_DOMAIN_ONLY_BCS = Set.of("customer")` to
  `HexagonalArchitectureTest`, downgrading Application and Infrastructure to
  `optionalLayer` for that BC. That is a manual per-BC registration, and
  `customer` is the only BC the sentence would currently describe. See WP-121.

**Fix.** Correct both, and add this plan to the documentation index.

**Done when.** Every stated count and version matches the code.

---
---

## WP-107 — UUIDv7 generator tests assert version, not monotonicity

**Status:** TODO · **Confidence:** REPORTED

**Where:** `RandomEventIdGeneratorTest`, `RandomReservationIdGeneratorTest`

**Defect.** Each asserts only `version() == 7`. UUIDv7 was chosen for
**time-ordered** IDs (index locality), and monotonicity is never asserted.
`RandomAccountIdGenerator` has no test at all.

**Fix.** Generate 1000 IDs and assert strictly increasing order; add the missing
third test (asserting v4 for `AccountId`, per WP-90).

**Done when.** All three generators are tested for the property they were chosen
for.

---
---

## WP-108 — No `.env.example` template

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-06

**Where:** repository root

**Defect.** `.env` is gitignored but required for local bring-up, and no template
exists. Onboarding friction.

**Fix.** Commit `.env.example` with all keys and placeholder values; reference it
from the README bring-up section.

**Done when.** A fresh clone can be brought up from the template alone.

---
---

## WP-109 — Spotless ratchet leaves the existing tree unchecked

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `pom.xml` → `<ratchetFrom>origin/main</ratchetFrom>`

**Defect.** Only files changed versus `origin/main` are format-checked. The
POM-level setting cannot be overridden from the command line, so there is no
ad-hoc way to audit the whole tree. This is a structural blind spot, **not**
evidence of unformatted code — the codebase was formatted from early on.

**Fix.** A weekly CI job running Spotless with the ratchet disabled via a
profile, so full-tree drift is detected without slowing PRs.

**Done when.** A scheduled full-tree format check exists.

---
---

## WP-110 — Gatling assertions never run in CI

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `src/test/java/gatling/AccountSimulation`; `.github/workflows/ci.yml`

**Defect.** The simulation asserts p50 < 10ms, p95 < 50ms, p99 < 100ms, and 100%
success. Nothing ever checks them.

**Fix.** It needs a running stack, so per-PR is wrong — add a scheduled workflow
against a compose stack to turn the assertions from documentation into a gate.

**Done when.** A scheduled run executes the simulation and fails on regression.

---
---

## WP-111 — No SBOM generation

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-34

**Where:** `pom.xml`

**Defect.** No `cyclonedx-maven-plugin`. Also relevant because the build pulls
from an extra remote repository (Confluent) beyond Maven Central, widening the
trust surface.

**Fix.** Add `cyclonedx-maven-plugin` with `makeAggregateBom` bound to `package`;
upload `target/bom.json` as a CI artifact.

**Done when.** Every build produces an SBOM.

---
---

## WP-112 — No failsafe/surefire split

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-71

**Where:** `pom.xml`

**Defect.** The Testcontainers tests run in surefire alongside the domain unit
tests, so there is no way to get sub-second domain feedback without paying for
Postgres startup.

**Fix.** Rename integration tests to `*IT` and add `maven-failsafe-plugin`,
giving a fast `mvn test` and a complete `mvn verify`.

**Done when.** `mvn test` runs only unit tests and completes in seconds.

---
---

## WP-113 — Sonar organization declared in two places

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `pom.xml` (`<sonar.organization>`); `.github/workflows/ci.yml`
(`${{ vars.SONAR_ORGANIZATION }}`)

**Defect.** Two sources of truth that will drift.

**Fix.** Keep one — prefer the CI variable, and remove the POM property.

**Done when.** The organization is declared once.

---
---

## WP-114 — CI uses bare `mvn`, not the pinned wrapper

**Status:** TODO · **Confidence:** VERIFIED

**Where:** `.github/workflows/ci.yml`; `.mvn/wrapper/maven-wrapper.properties`

**Defect.** The wrapper pins Maven 3.9.11, but CI invokes bare `mvn` — whatever
the runner ships. The README also says `mvn` throughout, so the pinned version is
used by almost no one. Additionally, `distributionType=only-script` with no
`distributionSha256Sum` means the wrapper downloads Maven unverified.

**Fix.** Use `./mvnw` in CI and the README; add `distributionSha256Sum`. Also
consider `verify` instead of `install` in CI — nothing consumes the installed
SNAPSHOT.

**Done when.** CI and documentation both use the pinned wrapper.

---
---

## WP-120 — PITest cannot see record compact constructors (measured: low impact)

**Status:** WONTFIX · **Confidence:** VERIFIED
**Related:** WP-37, WP-38, WP-99, WP-106, WP-119

**Rationale.** Kept as evidence, not as work. The investigation's one actionable
finding is already owned by **WP-99**, and WP-99's fix is the opposite of what a
naive reading of the mutation report suggests — see *Do not "fix" this by pinning
version 0* below. This entry exists so the "our mutation score is overstated"
theory is not re-derived from scratch; the measurement below refutes it.

**This closes the score-inflation question only — it does not license inlining
validation back into compact constructors.** The aggregate barely moves, but
*per class* the filter hides a great deal: `AccountEventMeta` exposes 1 mutant
and hides 5. The extraction convention in `Nationalities.validated`,
`CustomerId.requirePresent` and `AdmissionPolicySnapshot.validRestrictions`
stays — it is what keeps the KYC bounds (at least one nationality, max 10) inside
the gate.

**Where:** `pom.xml` → `pitest-maven`; every record with an inline compact
constructor.

**Defect.** PITest's default `FRECORD` filter suppresses **every mutant inside a
record's canonical constructor**, including a hand-written compact-constructor
body. A record that validates inline is therefore invisible to the mutation gate;
one that delegates to a `private static` helper is not. The A/B is exact:
`account.AccountId` validates inline and yields **1** mutant (its `of` factory);
`customer.CustomerId` is the same class with validation extracted into
`requirePresent` and yields **2**.

**Impact — measured, and smaller than it looks.** Re-running with
`-Dfeatures=-FRECORD` and diffing the mutant sets against the baseline:

| | mutants | killed | |
|---|---|---|---|
| Hidden by `FRECORD` (record constructors only) | **53** | **52** | 98.1% |
| `account.domain` baseline | 103 | 93 | 90.3% |
| `account.domain` including constructors | 129 | 118 | **91.5%** |
| `customer.domain` including constructors | 127 | 125 | 98.4% |

Rows 3–4 are **baseline + constructor mutants only**, not what a
`-Dfeatures=-FRECORD` run prints: that flag also un-filters generated
`equals`/`hashCode`/`toString` and accessors, which drags `account.domain` to
276/181 = 65.6%. See the paragraph below on why not to run it that way.

**Counting note.** The FRECORD-off run contains 55 `<init>` mutants, but 2 belong
to `AccountApplicationService` — a plain class, never filtered, already in the
278-mutant baseline. Subtract the baseline before attributing anything to the
filter, or you overstate what it hides.

The hidden region is **not** a reservoir of untested code — it is almost entirely
covered, and including it moves `account.domain`'s score **up**, not down. The
entire yield of the investigation is one survivor:

```
SURVIVED  AccountEventMeta.<init>  changed conditional boundary   (version < 0L → <= 0L)
```

**Do not "fix" this by pinning version 0 as accepted.** The mutation report says
only that nothing distinguishes `< 0` from `<= 0` there. **WP-99** establishes the
correct resolution in the other direction: streams start at 1, so the guard should
*tighten* to `version < 1L` and a version-0 event should be **rejected**. WP-99's
"Done when" kills this survivor as a side effect. A test written from this entry
alone would assert version 0 is valid and would have to be deleted by WP-99.

**Two things this filter does *not* explain**, both of which look like it and are
not:

- `Money`'s `setScale(digits, RoundingMode.HALF_EVEN)` generates **zero mutants
  even with `FRECORD` off** — no default PITest mutator substitutes an enum
  constant argument. No refactor makes the rounding mode gate-visible. It is
  pinned by test instead (WP-119).
- `AccountTransaction` is an **interface**, not a record. Its lone survivor is in
  the `fingerprint()` default method (`replaced return value with ""`), which
  `FRECORD` never touched — that is WP-20's territory, and WP-20's "Done when"
  (a test pinning the digest input) kills it.

**Do not disable the filter** with `-Dfeatures=-FRECORD`. Measured: it also
un-filters generated `equals`/`hashCode`/`toString` and accessors, taking the run
to 596 mutants / 420 killed = **70%**, below the 80% gate. The gain — one
`AccountEventMeta` survivor, already owned by WP-99 — is not proportionate.

**Reproducing this.** Two gotchas cost real time:

- `mutatedMethod` is HTML-escaped in `mutations.xml`: match `&lt;init&gt;`, not
  `<init>`, or the constructor mutants appear to be zero.
- Diff the two runs as **multisets** against the baseline. PITest emits duplicate
  `(class, method, line, mutator)` tuples, so set-based diffing undercounts.

---
---

## WP-121 — `INCREMENTAL_DOMAIN_ONLY_BCS` is an ArchUnit exemption with no expiry

**Status:** TODO · **Confidence:** VERIFIED
**Related:** WP-37, WP-106

**Where:** `architecture/HexagonalArchitectureTest` →
`INCREMENTAL_DOMAIN_ONLY_BCS`

**Defect.** `86a4d3a` introduced a hardcoded exemption set, currently
`Set.of("customer")`, which downgrades Application and Infrastructure to
`optionalLayer` for the listed BCs. It exists because the `customer` BC is being
built incrementally and has a domain layer only, which is legitimate. Its own
comment states the intent — *"Remove a BC once it has application +
infrastructure, so the layering rule resumes catching an accidentally-missing
layer in a finished BC"* — but **nothing enforces removal**.

**Impact.** Low today: `optionalLayer` only permits a layer to be *empty*, and
the direction rules still apply, so no violation can slip through while the BC is
genuinely domain-only. The risk is on the other side of the transition — once
`customer.application` and `customer.infrastructure` exist and the entry is still
present, a finished BC permanently keeps optional outer layers and the rule stops
catching the case it was written for. This is the same silent-gap shape as WP-37:
the suite stays green while measuring less than it appears to.

**Fix.** Make the exemption self-expiring rather than trusting a comment. In the
test, assert **both** of the following for every BC named in
`INCREMENTAL_DOMAIN_ONLY_BCS`:

1. it has **zero** classes under `..<bc>.application..` and
   `..<bc>.infrastructure..` — so the build fails the moment a listed BC outgrows
   its exemption, naming the BC to remove;
2. it still appears in `detectBoundedContexts(classes)` — otherwise a renamed,
   deleted or misspelled entry passes condition 1 vacuously and the exemption
   never expires.

**Done when.** Adding a class under `customer.application` fails the architecture
suite until `customer` is removed from the set.

---

# Sequencing

Items that must land together or in a specific order. This list is **not
exhaustive** — check each item's own `**Sequencing.**` and `**Related:**` fields
before starting.

**Must land together**

- **WP-02 + WP-03 + WP-04** — the DLQ is one subsystem; fixing a third of it does
  not make it work.
- **WP-25** with its Prometheus scrape-target change — otherwise all application
  metrics go silent.

**Ordered dependencies**

- **WP-57 → WP-26 → WP-29** — WP-57 replaces the error body with `ProblemDetail`;
  doing WP-26 or WP-29's response-shape work first is guaranteed rework.
- **WP-27 → WP-28** — no trace context exists to propagate without a tracer.
- **WP-27 → WP-29** — no trace ID exists to put in MDC without a tracer.
- **WP-05 → WP-30** — no principal to attribute without authentication.
- **WP-10 → WP-30** — if actor fields go in the event payload, they are exactly
  the schema-evolution case WP-10 makes safe.
- **WP-02/WP-03 + WP-115 → WP-17** — WP-17 requires both a working DLQ and
  bounded retries, neither of which currently exists.
- **WP-61 → WP-60** — WP-61 may make the `occurred_at` index useful; do not drop
  it first.
- **WP-01 → WP-77** — WP-77's database-level case is the proof that WP-01 is
  fixed.
- **WP-34 → WP-35** — otherwise version drift simply recurs.
- **WP-21 ↔ WP-52** — WP-21 demotes `processed_reservations` to a lookup index,
  changing what WP-52 should assert.
- **WP-54 ↔ WP-98** — both concern how the version constraint violation is
  detected and interpreted.
- **WP-71 → WP-112** — the container lifecycle fix makes the failsafe split
  worthwhile.
- **WP-50 → WP-119** — WP-50 changes the exception `Money.ensureSameCurrency`
  throws; WP-119's cross-currency tests assert it. Reversed, WP-50 breaks three
  freshly written tests.

**Deadlines relative to other work**

- **WP-10** before the next event-shape change, not after.
- **WP-37** and **WP-56** — both done on the customer branch (`309734c`,
  `493acf4`). **WP-119** and **WP-120**, the follow-ups WP-37 revealed, are not
  branch-bound — but WP-119 is not free-floating either: see `WP-50 → WP-119`
  above.
- **WP-116** before the customer infrastructure layer merges.
- **WP-05** alongside the customer bounded context, which owns identity.

**Migration hazards** — WP-16, WP-59, WP-60, and WP-63 all involve DDL that takes
heavy locks, cannot run inside a transaction, or changes behaviour elsewhere.
**Read each item's implementation note before writing the migration** — the
naive one-liner is wrong in at least three of the four cases:

- **WP-16** — a bare `CREATE TABLE ... PARTITION OF ... DEFAULT` permanently
  breaks `manage_outbox_partitions`. Four coordinated changes are required.
- **WP-59** — `DROP INDEX CONCURRENTLY` needs a Flyway `.conf` sidecar with
  `executeInTransaction=false`.
- **WP-60** — the two indexes need *opposite* treatment; the partitioned-parent
  index cannot be dropped concurrently at all.
- **WP-63** — use `NOT VALID` plus a separate `VALIDATE CONSTRAINT`.

**WP-118** (Flyway core/plugin major mismatch) should be settled **before** the
above, since script-configuration and transaction semantics differ across Flyway
majors.

---

# Recurring theme

The happy paths in this codebase are strong and heavily tested. **Nearly every
serious defect above lives in code that handles or configures failure** — a
swallowed exception, a dead-lettering path that cannot publish, a gauge that
fails into the healthy band, alerts pointing at panels that do not exist, a
backoff whose bounds came from a constructor default rather than a decision.

When adding tests, weight them toward failure injection rather than additional
happy-path coverage.
