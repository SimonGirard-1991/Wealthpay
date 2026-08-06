# ADR-009: PII retention and erasure in the Customer context

## Status

Accepted

Records the decision tracked as **WP-116** ("No erasure strategy for PII in an
immutable event store") and the **B-2** position (PII, retention, erasure).
WP-116 gates the Customer persistence layer — *"Do not merge the customer
infrastructure layer before this decision is recorded"* — and this ADR is that
record. WP-116 itself stays open until the persistence layer implements what
follows.

## Open input

The **home authorization jurisdiction is not named anywhere in this repository**
and is not settled here. It is deliberately not a blocker for this ADR: the
retention period is five years under both the EU and UK regimes (see D2), so
only the citation moves, and D2 makes the period configuration rather than a
constant precisely so the answer can land late without a migration.

It *is* a blocker for the `licensed_country` seed rows, and is scheduled to be
named in the admission ADR.

## Context

The Customer context models personal data: email address, personal name, date
of birth, gender, country of residence, and a **set** of nationalities.
Nationality is the field closest to GDPR Art. 9 special-category territory — it
can proxy for ethnic origin — and being set-valued, any erasure design assuming
one scalar country per customer is wrong before it starts.

Two tables hold that data under long retention, and they are not symmetric:

- `customer.customer_status_transition` — the KYC audit log, append-only,
  keyed to a customer that exists.
- `customer.admission_decision` (+ `_match`) — the admission audit record,
  which for a **refused** applicant is personal data about someone who never
  became a customer and has no row in `customer.customer` at all.

The Account context is event-sourced behind append-only triggers and fans out
to Kafka; the Customer context is state-stored. That asymmetry is what makes
the decision below cheap, and it is worth stating why: immutability is an
excellent audit substrate and a poor erasure substrate, so the question is not
"how do we delete from an immutable log" but "why would personal data ever be
in one".

## Decision

### D1 — Pseudonymisation: personal data never enters an append-only or replicated substrate

Personal data lives **only** in mutable tables in the `customer` schema, which
are deletable by the privileged purge role of D5 — not by the application, which
never deletes a customer. No event, outbox row, Kafka message, snapshot, or
projection carries it. Identity crossing any of those boundaries is a
`CustomerId` and nothing else.

This is not a change to make — it is a property the codebase already has, and
this ADR converts it from an accident into a rule. Verified at the time of
writing: every `account` domain event carries only `AccountEventMeta`
(`EventId`, `AccountId`, `Instant`, `version`) plus `Money`,
`SupportedCurrency`, `TransactionId` and `ReservationId`. There is no personal
data in the event store today.

**Enforcement already exists for the Account side, for free.** `account` is a
`CLOSED` Spring Modulith module permitted to depend only on `shared`, so
`account.domain.event` is structurally incapable of referencing
`customer.domain.model.PersonalName`. `ArchitectureTests` fails the build if
that boundary is weakened. Anyone relaxing the module rule should know they are
also relaxing a data-protection control.

**Enforcement is owed on the Customer side when Seam B lands.** Seam B's
published language (`CustomerVerified | Suspended | Closed`) is specified as a
versioned, primitive-only contract of lifecycle transitions, so it carries no
personal data by construction — but "by construction" is an assertion until
there is a schema review rule behind it. Seam B must not ship without one.

### D2 — Retention is jurisdiction-parameterised configuration, not a constant

A hardcoded five-year constant would be wrong on today's law and wrong again
next July.

| Regime | Period | Trigger | Extension ceiling |
|---|---|---|---|
| 4AMLD Art. 40(1)(a)–(b) | 5 years | end of business relationship, or date of an occasional transaction | +5 years max, Art. 40(1) 2nd subpara |
| AMLR Art. 77(3) *(applies 10 Jul 2027)* | 5 years | end of relationship · occasional transaction · **date of refusal** | +5 years, case-by-case only |
| UK MLR 2017 reg. 40(3)–(4) | 5 years | relationship end / transaction complete, on a knowledge test | 10 years (reg. 40(4)) |

Two reasons the number cannot be literal:

1. **It is not uniform today.** Directive (EU) 2015/849 is a *directive*, and
   its Art. 5 expressly permits member states to *"adopt or retain in force
   stricter provisions"*. Five years is a floor implemented per member state,
   not an EU-wide value.
2. **Its legal basis changes on a known date.** Regulation (EU) 2024/1624
   (AMLR) is in force since 9 July 2024 but **applies from 10 July 2027**
   (Art. 90), on which date Directive (EU) 2024/1640 Art. 77 repeals 4AMLD. At
   that point retention becomes directly applicable and uniform, and under Art.
   77(3) the extension mechanism becomes a **competent-authority power
   exercisable "on a case-by-case basis"** rather than the member-state
   discretion 4AMLD Art. 40(1) conferred. Blanket national extension therefore
   ceases to be the mechanism — which is the scoped version of the claim, and
   the one to quote.

Therefore: retention is the property `customer.retention.cdd-years`, defaulting
to `5`. It is never a literal in SQL, in Java, or in a migration comment.

### D3 — Five categories of personal data, and only one has a usable anchor today

Retention is meaningless without the date the clock starts from. Enumerated per
category, because they do not share one:

| # | Category | Anchor | Status |
|---|---|---|---|
| 1 | Refused applicant | date of refusal → `admission_decision.decided_at` | **usable**; lands with this increment |
| 2 | Admitted applicant's decision record | inherits the customer's anchor, via the D6 link | **link** lands with this increment; the anchor it inherits is row 4, which does not exist |
| 3 | Customer, relationship live | none — the clock has not started | correct by design, not a gap |
| 4 | Customer, relationship ended | end of the relationship → the `CLOSED` transition | **does not exist** |
| 5 | Registered but never activated | none | **gap — see below** |

Read the Status column carefully: rows 2, 4, and 5 are unresolved in three
different ways, and only row 1 gives a purge job a date it can compute today.

The refusal anchor is not a convenience. **AMLR Art. 77(3) names the date of
refusal as a retention trigger in its own right**, alongside relationship end
and occasional transaction. `admission_decision.decided_at` is the statutory
anchor, which is a second, independent justification for that column beyond the
"how many sanctions refusals last quarter" query it was added for.

**The relationship-end anchor is uncomputable today.** `CustomerStatus` is
`{ONBOARDING, ACTIVE}`; there is no closure state, so the value cannot be read.
For a customer whose relationship is live this is correct rather than missing —
the clock genuinely has not started.

**The abandoned-onboarding category has no anchor at all.** An applicant who
passes admission and registers but never activates is neither "relationship
live" nor "refused". In a real onboarding funnel this is likely the
highest-volume personal-data category in the schema, and it is reachable by
Art. 5(1)(e) storage limitation without waiting for any AML trigger, since the
purpose the data was collected for was never fulfilled. Resolving it needs an
abandonment definition — `registered_at` plus a stated window — which is a
product decision belonging with the KYC status axis. Recorded here so it is not
discovered by a regulator.

Note the symmetry with D5: the zero-transition `ONBOARDING` customer is the hole
in `RESTRICT` *and* the hole in this table. It is the same customer.

**When the purge path becomes mandatory — a disjunction, not a single trigger.**
It was tempting to tie the prerequisite to offboarding alone. That is wrong,
because the refusal clock is unconditional and runs on wall-clock time whether
or not closure is ever built:

> The purge path must exist before **whichever comes first**: the `CLOSED`
> transition shipping, or **first production refusal + `cdd-years`**.

Until then v1 is compliant, but note the difference in kind: for the customer
categories that is compliance *by construction* (no anchor has started); for
refused applicants it is compliance *by calendar*, and calendars expire without
anyone deciding anything. The AMLR refusal trigger becomes explicit on
10 July 2027 regardless.

### D4 — Erasure is suspended, then mandatory. It is a sequence, not a conflict

The framing this ADR supersedes described AML retention and the right to
erasure as "a genuine conflict". They do not conflict; they apply in sequence,
and reading them as a conflict invites a reconciliation mechanism that is not
needed.

**During the retention window**, GDPR Art. 17(3)(b) disapplies the right to
erasure where processing is necessary *"for compliance with a legal obligation
which requires processing by Union or Member State law to which the controller
is subject"*. AML record-keeping is that obligation. An erasure request in this
window is **refused, with a reasoned response** under Art. 12(4) — not honoured,
and not silently ignored.

Note the chapeau: paragraphs 1 and 2 are disapplied *"to the extent that"*
processing is necessary. Art. 17(3)(b) **suspends** the right for a bounded
period; it does not override it. "AML beats the right to be forgotten" is the
common imprecision and it is the one that produces retain-forever designs.

**At expiry**, deletion is mandatory from both directions at once:

- AML law imposes it positively — 4AMLD **Art. 40(1), second subparagraph**:
  *"Upon expiry of the retention periods … Member States shall ensure that
  obliged entities delete personal data"*; AMLR Art. 77(3): *"obliged entities
  shall delete personal data upon expiry of the five-year period"*; UK MLR 2017
  reg. 40(5) to the same effect.
- Data-protection law imposes it independently — Art. 5(1)(e) storage
  limitation, Art. 17(1)(a) (no longer necessary) and Art. 17(1)(e) (erasure
  required for compliance with a legal obligation).

*Citation precision, because it is easy to get wrong:* in the consolidated
5AMLD-amended text the deletion duty is the **second** subparagraph of Art.
40(1). It was the final subparagraph in the original 2015 text; the final
subparagraph today is the unrelated Art. 32a centralised-mechanisms rule.

### D5 — Append-only is enforced by triggers. Privileges and referential actions are defence in depth

The audit tables carry retention-bound evidence, so "append-only" has to be a
constraint rather than a convention. There are three ways to defeat it silently,
and only one mechanism closes all three.

**Verified against PostgreSQL 18** (two runs: one as a non-owner role, one as
the owning superuser):

| Attempt | `REVOKE` only | `ON DELETE RESTRICT` | trigger, default (`tgenabled='O'`) | trigger, `ENABLE ALWAYS` (`'A'`) |
|---|---|---|---|---|
| Direct `DELETE` on the child, non-owner role | blocked | n/a | blocked | blocked |
| Direct `DELETE` on the child, **owner / superuser** | **succeeds** | n/a | blocked | blocked |
| `DELETE` the parent, child FK is `ON DELETE CASCADE` | **succeeds, zero audit rows left** | n/a | blocked, statement rolls back | blocked |
| `DELETE` the parent, child FK is `ON DELETE RESTRICT` | n/a | blocked | blocked | blocked |
| `TRUNCATE parent CASCADE` | **succeeds** | **succeeds** | row-level **succeeds**; statement-level `BEFORE TRUNCATE` blocks | blocked |
| `SET session_replication_role = replica`, then `DELETE` | **succeeds** | **succeeds — FK skipped, orphan row left behind** | **succeeds** | blocked |

Four findings drive the rule:

1. **Referential actions are not privilege-checked against the caller.**
   PostgreSQL implements them with internal system triggers running as the
   **table owner**, so `ON DELETE CASCADE` deletes rows the caller is explicitly
   forbidden to delete, with no error.
2. **`REVOKE` is inert in this deployment.** The application, Flyway, and
   Debezium all connect as `POSTGRES_USER=user` — the bootstrap superuser, which
   owns every table Flyway creates, and a superuser bypasses ACL checks
   entirely. So a `REVOKE` against the runtime role is documentation, not a
   control, and this is stronger than "undone by one careless `GRANT`": there is
   nothing to undo. **No non-owning role exists on the write path.** The one
   non-superuser role in the stack is the read-only `monitoring` role created by
   `docker/postgres/bootstrap/02-monitoring-role.sql` for postgres-exporter —
   which is also the natural, already-compose-wired home for the non-owning
   application role and the privileged purge role this ADR keeps deferring to.
3. **`TRUNCATE ... CASCADE` defeats both `RESTRICT` and row-level triggers**,
   because row triggers do not fire on `TRUNCATE` and the referential action is
   not consulted. Only a statement-level `BEFORE TRUNCATE` trigger stops it.
4. **`SET session_replication_role = replica` defeats triggers *and* referential
   actions in one statement**, leaving an orphaned audit row pointing at a
   deleted parent — a state neither the schema nor the aggregate can represent.
   It is `SUSET`, so per finding 2 this application can issue it. This is
   *cheaper* than the "one careless `GRANT`" objection that finding 2 dismisses,
   so a trigger design that ignores it inherits the flaw it was chosen to fix.
   `ALTER TABLE ... ENABLE ALWAYS TRIGGER` (`tgenabled = 'A'`) restores the
   guard under replica mode — verified.

Therefore:

- **Every audit table carries `BEFORE UPDATE`, `BEFORE DELETE` (row-level) and
  `BEFORE TRUNCATE` (statement-level) triggers that `RAISE EXCEPTION`, each
  declared `ENABLE ALWAYS`.** This is the enforcement. Reuse the house pattern
  from `V3__event_store_append_only.sql`, plus the `TRUNCATE` and `ENABLE
  ALWAYS` additions above. Applies to `customer_status_transition`,
  `admission_decision`, `admission_decision_match`, and the D6 link table.
- **The purge job disables the guard with `ALTER TABLE ... DISABLE TRIGGER
  <name>` inside its transaction — never with `session_replication_role`.**
  Verified: the `ALTER` idiom still works against an `ENABLE ALWAYS` trigger,
  takes only a `ShareRowExclusiveLock`, and is transactional. The two idioms
  look interchangeable and are not: one is the maintenance path, the other is
  finding 4's attack, and `ENABLE ALWAYS` deliberately breaks the second.

**Scope the trigger claim honestly, since this ADR has just demolished one
overstated control.** Triggers are not unbypassable by a superuser: `DROP
TRIGGER`, `DISABLE TRIGGER` and `DROP COLUMN` all defeat them, and the last is
worth naming — dropping `admission_decision_match.triggering_country` fires no
trigger, leaves every row in place, and erases the evidence they exist to hold.
What triggers buy is that **every remaining path is deliberate, DDL-visible, and
auditable**, whereas `REVOKE` against an owning superuser requires no act at all
and leaves no trace. That is the claim to make, and it is enough to justify the
inversion.
- **Every FK from an audit table is `ON DELETE RESTRICT`** —
  `customer_status_transition.customer_id`,
  `admission_decision_match.decision_id`. Now defence in depth rather than the
  guarantee, and it keeps its own hole: an `ONBOARDING` customer has **zero**
  transition rows, so `RESTRICT` never engages for them.
- **FKs from aggregate components are `ON DELETE CASCADE`** — on
  `customer_nationality`, which the schema plan defines as the **composite** FK
  `(customer_id, kind) → customer(id, kind)`; the action is declared there.
  The asymmetry is the point: nationalities are part of the customer's state and
  should die with it, whereas a transition record is evidence *about* the
  customer and must not.
- **`REVOKE UPDATE, DELETE, TRUNCATE` on the audit tables and `REVOKE DELETE ON
  customer.customer` are still declared**, as defence in depth and as
  documentation of intent — but they become real controls only once a
  non-owning runtime role exists.
- **Purging is not an application capability.** It is a privileged, ordered,
  audited operational job that deletes children before parents — link table,
  match rows, transitions, then the customer and the decision — using the
  `ALTER TABLE ... DISABLE TRIGGER` idiom above. **It does not exist yet**
  (see D3).

**Two gaps this exposes beyond the Customer context**, recorded because they
have the same signature as the defect above:

- **There is no role separation on the write path.** The application, Flyway, and
  Debezium all connect as the owning superuser; only the read-only `monitoring`
  role is separated. Until the write path is split, every privilege-based
  control in this repository is advisory. Tracking that is outside this ADR's
  scope, but no future design should cite a `GRANT`/`REVOKE` as an enforcement
  mechanism until it is done.
- **`account.event_store`'s existing append-only triggers are bypassable on two
  of the four paths.** V3 declares only `BEFORE UPDATE` and `BEFORE DELETE`, at
  default `tgenabled`, so both `TRUNCATE` and `session_replication_role` defeat
  them. The fix is a forward migration adding the statement-level `BEFORE
  TRUNCATE` trigger **and** `ENABLE ALWAYS` on all three — not a single trigger,
  and not an edit to V15-era migrations, which are immutable.

### D6 — The admission record stands alone; the idempotency table is not an audit table

`admission_decision` carries no `customer_id`, so an admitted decision's only
structural path to its customer runs `idempotency_key → processed_registrations
→ customer_id`. That tempts pinning `processed_registrations` retention to the
audit retention. **Rejected.**

`processed_registrations` is an idempotency-dedup table whose natural retention
is hours to days. Pinning it to five years would grow a hot operational table
without bound to serve an audit purpose, and — worse — would silently convert
it into a personal-data store carrying its own retention and erasure duty, in a
table nobody thinks of as holding personal data.

Instead, `processed_registrations` may be pruned on an operational schedule —
but that is only safe once the two outcomes are separated, because they have
different anchors and only one of them is self-contained.

**A refused decision is self-contained.** It stands on its own evaluated inputs
and its own `decided_at` anchor, and needs no join to be meaningful five years
later.

**An admitted decision is not, and pruning would strand it.** It is the
onboarding screening evidence for a real customer, which makes it CDD material
under 4AMLD Art. 40(1)(a) — retained until **relationship end + `cdd-years`**,
not decision + `cdd-years`. Once `processed_registrations` is pruned, its only
path to that customer is gone, leaving a purge job two broken options: anchor on
`decided_at` and destroy screening evidence years *inside* the mandatory window,
or have no anchor and retain forever, which is the D4 deletion duty unmet.

**Therefore an admitted decision must be linked to its customer by a row written
in the transaction that creates the customer** (ALT-9b step 5):

```sql
CREATE TABLE customer.customer_admission (
  customer_id UUID        NOT NULL REFERENCES customer.customer(id)           ON DELETE RESTRICT,
  decision_id UUID        NOT NULL REFERENCES customer.admission_decision(id) ON DELETE RESTRICT,
  linked_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (customer_id, decision_id),
  UNIQUE (decision_id)          -- a decision anchors to at most one customer
);
```

Append-only under D5, like the tables it joins. Both FKs are `RESTRICT`: a
`CASCADE` on `customer_id` would destroy the anchor while the evidence it dates
survives, which is this section's failure mode reintroduced from the other end.

**`UNIQUE (decision_id)` carries the cardinality constraint**, and it is the
only one that does — a customer may legitimately hold more than one decision
(see the concurrency case below), a decision may not be held by more than one
customer. The composite primary key therefore adds an *index*, not a constraint:
it covers the purge job's customer → decisions traversal, which is the direction
retention is computed in. `PRIMARY KEY (decision_id)` plus an index on
`customer_id` would be equally correct; what matters is that both traversal
directions are covered and `decision_id` is unique.

**Write path, all four branches — this is the part that is easy to get wrong:**

| ALT-9b step 5 branch | Link written? |
|---|---|
| Created | **yes**, atomically with the customer row |
| Replay (idempotent short-circuit) | **yes** — using the stored `customer_id` and *this attempt's* `decision_id`. Each attempt mints a fresh `decision_id` at step 3, so no conflict is reachable; add `ON CONFLICT DO NOTHING` only as defence against an in-transaction retry, not as the deduplication mechanism |
| `IN_FLIGHT` | no — a retry-later, not a success |
| Refused | never reaches step 5; throws at step 4 |

**The replay branch is not an edge case, and omitting it recreates the orphan
this section exists to prevent.** ALT-9b already accepts that two concurrent
requests sharing an idempotency key each commit their own `admission_decision`
row at step 3 — "one extra decision row; benign". That verdict was correct for
auditability and this decision changes what the duplicate costs: without a link
on the replay branch the outcome is **two `ADMITTED` rows, one customer, one
link**, and the unlinked row is CDD evidence with no anchor — the retain-forever
branch above, reached by a path ALT-9b calls benign.

**Disposition for an unlinked `ADMITTED` decision:** anchor it on `decided_at`,
as if it were a refusal. Because customer creation and the link are atomic, an
unlinked row normally means no customer was created — a crash between steps 3
and 5, which ALT-9b already accepts as "accurate, not corrupt". The residual is
a replay attempt that crashes before linking, which would anchor a real
customer's decision earlier than relationship end. That is rare and fails in the
conservative direction for storage but the wrong direction for evidence, so
**make it observable rather than silent**: a scheduled check counting `ADMITTED`
decisions with no link older than a short window, alerting on non-zero.

Two properties survive from the shape that dropped `customer_id`:

- It does not reintroduce the phantom reference. That objection applies at step
  3, where no customer row exists in any scenario. At step 5 one does, and the
  link is atomic with it.
- A refused decision never acquires a link, so the two outcomes remain
  structurally distinguishable — which is what made dropping `customer_id`
  right in the first place.

### D7 — Personal data never reaches logs, metric labels, or exception messages

This extends the existing convention that exception messages never echo the
value that failed validation — **a convention that holds in the domain and does
not hold at the transport layer**, which is precisely where the request payload
lives. Every `throw` under `customer/domain/model/` is clean today. The web
layer is not (channel 4 below).

- **Prohibited** in log lines, metric labels, and exception messages: email
  address, personal name, date of birth, gender, nationalities, country of
  residence, and **`triggering_country`**. The last is the non-obvious one:
  "refused, nationality, RU" attached to a request is personal-data-adjacent
  and belongs in `admission_decision`, not in a time series.
- **Permitted** as metric labels: `restriction`, `connecting_factor`, and
  subject type. All three are low-cardinality closed enums and none identifies
  a person.

**Scope this honestly — the three channels have three different strengths, and
one of them is a live defect.**

1. **Metric labels — testable.** The admission counter's tag keys are
   constructed in one place, so a unit test can assert the tag-key set is
   exactly that triple, and a new label fails the build.
2. **Log statements — convention.** Enforced by review, not by a constraint.
   Claiming otherwise would assert a guarantee this ADR does not deliver.
3. **Exception messages — a known live leak, not merely unenforced.**
   `shared/infrastructure/web/GlobalExceptionHandler` declares a catch-all
   `@ExceptionHandler(Exception.class)` that returns `e.getMessage()` in the 500
   body. pgjdbc propagates the server `DETAIL` — including
   `Failing row contains (…)` — into the message by default, so a constraint
   violation on `admission_decision_match` has a live path to a response body
   containing the sanctions match that triggered a refusal. That is also an AML
   tipping-off exposure, which in most jurisdictions is a criminal matter rather
   than a hygiene one. **Fixing that handler to return a constant body, keeping
   the detail in the log, is a prerequisite of shipping the admission flow** —
   not a follow-up. **The exposure is exactly one method in one class.**
   `AccountExceptionHandler` declares no catch-all; its 5xx handlers are scoped
   to curated domain exceptions whose messages carry no values, so a driver
   exception falls straight through to the shared handler. That is good news for
   the sequencing decision below, because it shrinks the rework being accepted.
   *(Not `logServerErrorDetail=false` as the fix: that strips the `DETAIL` from
   the logs too, which is the half worth keeping. Useful only as defence in
   depth.)*
4. **Validation error bodies — a second live leak, and the fix is free.**
   `GlobalExceptionHandler`'s `MethodArgumentNotValidException` handler builds
   `FieldErrorDto.rejectedValue(...)` into the **400** body, and the
   accompanying `log.warn` puts the same values in the logs. A Bean Validation
   failure on `dateOfBirth`, `email` or a name therefore echoes that value
   straight back to the caller — a direct hit on this rule's own prohibited
   list. **Ceasing to populate it is not a contract change:**
   `openapi/shared-api.yaml` declares `FieldError` with
   `required: [field, message]` and `rejectedValue` as optional and nullable, so
   every conformant client already handles its absence. Only deleting it from
   the schema would be breaking, and that is not required.

The distinction matters because a reader who sees only "convention" will assume
review is the residual risk. For channels 3 and 4 the residual risk is existing
code that already does the wrong thing.

**Sequencing, stated because it contradicts an existing plan.** WP-26 covers
these handlers and sequences them behind WP-57's migration of the whole error
surface to `ProblemDetail`, on the sound grounds that a bespoke body shape now
is guaranteed rework. **This ADR jumps that queue for channels 3 and 4**, on
different grounds each:

- **Channel 3** because tipping-off is a criminal-liability matter rather than
  an information-disclosure one. The rework is accepted as the price, and per
  the note above it is one method in one class.
- **Channel 4** because there is no rework to trade against: not populating a
  field costs one deleted statement, survives the `ProblemDetail` migration
  untouched, and deferring it means knowingly echoing dates of birth and email
  addresses for the duration of an unrelated refactor.

Channels 1 and 2 stay behind WP-57.

### D8 — Position per substrate

WP-116 requires an explicit position for each place a copy could accumulate.

| Substrate | Position |
|---|---|
| Kafka topics | Customer publishes nothing in v1, and Seam B's contract is primitive-only lifecycle transitions — so topic retention is not a data-protection control. **But see the CDC note below: what keeps customer rows off Kafka is a connector filter, not the absence of a producer.** |
| Outbox partitions | No Customer outbox in v1. When Seam B lands its payload is the Seam B event, which carries no personal data. |
| Read-model projections | None for Customer. |
| Snapshots | None. Customer is state-stored, not event-sourced, so there is no snapshot substrate to erase from. |
| Backups / PITR | **The residual — see below.** |

**The CDC pipeline filters customer data in the wrong place, and two independent
paths put it there.** "Customer publishes nothing" is true of the application,
but Debezium subscribes to the database's replication stream, not to the
application. The connector sets neither `publication.name` nor
`publication.autocreate.mode`, whose defaults are `dbz_publication` and
`all_tables`; and `V15__outbox_publication.sql` creates the same object `FOR ALL
TABLES` in its `ELSE` branch when it does not already exist. Under this repo's
documented startup order the connector wins the race and V15 takes its `ALTER`
branch — but **both paths land on `FOR ALL TABLES`, which is why narrowing
either one alone is not the invariant.** From the moment this increment creates
the customer tables,
every customer row image — names, dates of birth, nationalities — is decoded
into the replication stream and shipped to the Kafka Connect JVM, where two
connector-side filters (`table.include.list` and `schema.include.list`, both in
an unversioned shell script) are all that stop it reaching a topic.

That is a filter, not a boundary, and D1's method is to name the control rather
than rely on the outcome.

**Setting `publication.autocreate.mode=filtered` alone does not fix this.**
`filtered` only takes effect when no publication exists, and **nothing in the
system ever narrows one that already does** — V15's `ALTER` branch adjusts only
`publish_via_partition_root`. So on any database either path has already touched,
the setting is inert. **The invariant requires a forward migration that replaces
`dbz_publication` with a `FOR TABLE account.outbox` publication, ordered ahead
of the customer schema's first migration**, with the connector setting as the
belt-and-braces half.

This overlaps WP-22, which already prescribes the connector setting and carries
the constraint this ADR must respect: **migrations are immutable — do not edit
V15; fix forward with a guarded replacement.** The data-protection requirement
raises that item's priority; it does not change its shape.

**Backups are the one place erasure does not reach, and D1 does not fix it.** A
hard delete at the primary does not reach WAL archives or existing backups.
This is accepted and recorded rather than solved: erasure is effective at the
primary, and the residual is bounded by backup retention. That makes a **stated
backup retention** a prerequisite for claiming erasure is complete — which the
repository does not have, and which is tracked separately as the backup/PITR
work. Crypto-shredding is the only mechanism that reaches backups, and it is
rejected below; this is the price of that rejection, stated plainly.

## Consequences

### Positive

- Erasure is a `DELETE` against ordinary mutable tables. No key store, no
  re-encryption, no new failure mode on any critical path.
- The Account append-only triggers and the outbox/CDC pipeline are preserved
  exactly as designed, rather than worked around.
- The Modulith `CLOSED` boundary is doing data-protection work at zero cost,
  and now says so.
- The refused-applicant record is self-contained, so admission auditing needs
  no join and survives operational pruning of the idempotency table.
- Retention survives 10 July 2027 as a configuration change.

### Constraints and invariants

- No personal data in any event, outbox row, Kafka message, snapshot, or
  projection. `CustomerId` only.
- Every audit table carries `BEFORE UPDATE`, `BEFORE DELETE` and
  `BEFORE TRUNCATE` triggers, each **`ENABLE ALWAYS`**. That is the enforcement.
  `ON DELETE RESTRICT` on audit FKs and the `REVOKE`s are defence in depth; the
  `REVOKE`s do nothing until a non-owning runtime role exists.
- Maintenance suspends the guard with `ALTER TABLE ... DISABLE TRIGGER` inside a
  transaction. **`session_replication_role = replica` is forbidden** — it is
  finding 4's bypass, and it skips referential actions too.
- No design may cite a `GRANT`/`REVOKE` as an enforcement mechanism while the
  application runs as the owning superuser.
- Every `ADMITTED` decision whose registration reached ALT-9b step 5 — created
  **or replayed** — carries a `customer_admission` link. An unlinked `ADMITTED`
  decision is anchored on `decided_at` and is an alertable condition.
- Retention is read from `customer.retention.cdd-years`, never inlined.
- Metric labels are restricted to `restriction`, `connecting_factor` and
  subject type.
- Before the admission flow ships: the catch-all exception handler returns a
  constant body, and the validation handler stops populating `rejectedValue`.
  Both deliberately jump WP-26/WP-57's sequencing; nothing else does.
- A forward migration replaces `dbz_publication` with `FOR TABLE
  account.outbox`, ordered ahead of the customer schema's first migration.
  `publication.autocreate.mode=filtered` is the belt-and-braces half, not the
  fix. V15 is immutable.
- Seam B may not ship without a schema-level control that keeps its published
  language primitive-only.
- The purge path must exist before whichever comes first: the `CLOSED`
  transition, or first production refusal + `cdd-years`.

### Negative

- **No purge path exists.** Nothing is due for deletion yet (D3), so this is
  correctly sequenced rather than a live breach — but the deletion duty in D4 is
  mandatory, not discretionary, and for refused applicants the deadline runs on
  wall-clock time whether or not anyone revisits this.
- **Abandoned onboarding has no retention anchor** (D3). Probably the
  highest-volume personal-data category in the schema, and the one reachable by
  Art. 5(1)(e) without any AML trigger.
- **No database role separation exists**, so every privilege-based control in
  this repository — here and in the Account context — is advisory until it does.
  D5 works around it with triggers rather than depending on it.
- **Two web-layer channels leak today** (D7): the 5xx catch-all via
  `e.getMessage()`, and the 400 validation body via the published
  `rejectedValue` field. Both are existing code doing the wrong thing, not
  unenforced conventions, and the first carries tipping-off exposure rather than
  only privacy exposure. Removing `rejectedValue` is a contract change.
- **Triggers are defeatable by a deliberate act.** `DROP TRIGGER`, `DISABLE
  TRIGGER` and `DROP COLUMN` all work for a superuser, and `DROP COLUMN` erases
  evidence without firing anything. The guarantee is that every remaining path
  is DDL-visible and auditable, not that none exists.
- **Erasure does not reach backups.** Accepted residual (D8), bounded by a
  backup retention that is itself not yet defined.
- **The log rule is a convention.** Only the metric-label half is enforceable
  today.
- **Refused-applicant data is retained without the subject's involvement**, and
  under D3 for `cdd-years` from `decided_at`. That is the law working as
  intended, but it is personal data held about people we declined, and it
  should be visible in a privacy notice rather than only in an ADR.

## Alternatives Considered

### Crypto-shredding — store personal data encrypted per subject, erase by destroying the key

The stronger guarantee, and the standard answer when personal data genuinely
must live in an immutable substrate. Rejected for four reasons, in order of
weight:

1. **D1 removes the premise.** Crypto-shredding earns its cost when personal
   data must be in an append-only log. Customer is state-stored and publishes
   nothing, so it need not be.
2. **It defeats the storage model deliberately chosen for reporting.** Customer
   subtype data is stored in per-subtype columns rather than JSONB *because*
   FATCA/CRS reporting queries date of birth, names, and nationality directly.
   Ciphertext is neither queryable nor indexable, so this would trade the
   reporting path for the erasure path.
3. **The prerequisite does not exist.** There is no secret store in this
   project — the same unbuilt dependency that currently blocks key custody for
   the customer-number generator. Adopting crypto-shredding here would make a
   data-protection guarantee depend on infrastructure nobody has built.
4. **It does not solve the actual blocker.** The binding problem in D3 is that
   the retention clock has no anchor. Key destruction does not supply one.

Field-level encryption with key rotation is a variant of this, not a third
option, and **rotation is not erasure**.

### Treat erasure as always honourable — hard-delete on request

Rejected: unlawful during the retention window. AML record-keeping is a legal
obligation under Art. 17(3)(b), and deleting on request would destroy records
the firm is required to hold.

### Retain indefinitely

Rejected twice over: it breaches the positive deletion duty in D4, and it
breaches Art. 5(1)(e) storage limitation independently. It also exceeds the
statutory ceiling — even the extended period is capped at five additional years
under 4AMLD Art. 40(1), and narrows further under AMLR.

### Hardcode a five-year retention constant

Rejected. Five years is a floor under a directive that expressly permits
stricter national rules (Art. 5), not a uniform value, and its legal basis is
replaced on 10 July 2027. A constant would be wrong in some member states today
and structurally stale on a known date.

### `ON DELETE CASCADE` on the transition foreign key

The intuitive choice, and it silently voids the append-only guarantee — see the
verification table in D5. Rejected on empirical evidence, not on taste.
