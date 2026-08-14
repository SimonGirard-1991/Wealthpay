# WIP — Customer BC (handoff)

_Last updated 2026-08-13. Branch `feat/introduce-cutsomer-bc`. **Increments 1-3 committed. Increment
4 items 0, 1, 2, 3, 4, 5, 9, 12 and 15 done; item 6 part-done — the `CustomerStore` adapter landed,
the other three ports are still unimplemented.**_

_**Item 15 was taken BEFORE the rest of item 6, inverting the order this header previously gave.**
`ActivateCustomer` depends on `CustomerStore` alone, which landed in `eea4d72`; the three remaining
ports all serve the registration path, which is additionally blocked on item 7 and item 14. Building
them first would have added three more ports with no caller — the exact condition that produced item
5's 🔴 — and would first have required paying item 6's fixture-minting debt, which a mocked-port use
case does not touch. **The fixture-minting hoist that gated further item 6 work is done (2026-08-14,
see item 6). Order from here: finish item 6's three remaining adapters → item 14 (`evaluate`) →
item 8 (observability) → item 7 (FPE) → items 10, 11, 13.** Item 14 still has no dependencies and
may be pulled earlier, but must precede increment 5 item 1._

> # 🔴 THIS FILE MUST NOT REACH `main`. DELETING IT IS PART OF THE MERGE.
>
> It is the **epic tracker for the customer BC** — the running log of every decision, correction and
> falsified claim along the way. It is tracked **only for the life of this branch**, because
> `git clean -fd` was one keystroke away from destroying the only record of the polarity argument,
> the tipping-off rationale, the FATCA-vs-Reg S split and the Q1 counter-argument.
>
> **Being tracked does not make it durable documentation — it makes it recoverable.** Its content is
> raw material with a short half-life: it argues with earlier drafts of itself, records what was
> wrong before it was right, and is addressed to whoever picks the epic up next. None of that
> belongs in `main`.
>
> **Before this branch merges, IN THIS ORDER:**
> 1. **Item 13 first.** Extract the durable reasoning into **ADRs** (item 13 owns the admission ADR
>    and the ADR-009 amendment) and into general documentation where it is not a decision record.
>    **Deletion is itself a control that removes load-bearing constraints** — several rules exist
>    only here, so deleting before extracting silently drops them.
> 2. Fold anything still outstanding into the backlog or the issue tracker — **not** into this file.
> 3. **Then delete this file.** CI *reports* it: the `wip-guard` job fails any PR to `main` whose
>    tree still contains a `WIP-*` file. **It does not yet block anything** — `main` has no branch
>    protection and no rulesets, so the red check is advisory until `wip-guard` is added to the
>    required checks.
>
> **🔴🔴 ALREADY PUSHED — THE DECISION THIS SECTION EXISTED TO DEFER WAS MADE BY DEFAULT.**
> Verified 2026-08-13: `gh repo view` reports `SimonGirard-1991/Wealthpay` **PUBLIC**, and
> `git branch -r --contains 38f3996` — the commit that adds this tracker — returns
> `origin/feat/introduce-cutsomer-bc`. The text here previously read "the tracker is **local only**
> … so nothing has been published yet" and instructed the reader to **decide before pushing**. That
> window is closed. The old wording is not merely stale, it is *reassuring in the wrong direction*,
> which is why it is corrected in place rather than left with a note appended.
>
> - **The blob is public now.** Deleting the branch does not reliably unpublish it, and the moment a
>   PR exists GitHub retains the `refs/pull/` namespace independently of the branch — content stays
>   fetchable **even after squash-merge and branch deletion**.
> - **Squash-merge solves the wrong half.** It keeps the file out of `main`'s history — real, and
>   still worth doing — but it does not make a published blob unreachable.
> - **What is public** is a dated, candid inventory of unmitigated compliance gaps at a regulated
>   bank ("no purge path exists", "erasure does not reach backups", "nothing in the database enforces
>   that premise").
>
> **The contingency is therefore now the requirement, not one branch of a choice: every gap named in
> this file needs a tracked owner**, because the list is citable by anyone. That re-ranks item 13
> from a merge chore to the mechanism that converts a public *gap list* into a public *owned* list.
>
> **🔴 One cheaper option is still open, and it closes the moment a PR is opened.** Verified
> 2026-08-13: `gh pr list --state all` shows **no PR for this branch**. The `refs/pull/` retention
> described above attaches when a PR exists — so *today*, deleting or rewriting the remote branch is
> materially more effective than it will be an hour after the PR is opened. Ranked by what each
> actually retracts: **(1)** delete/rewrite the remote branch now, before any PR — the only option
> that removes the reachable ref; **(2)** take the repository private; **(3)** keep it public and own
> the list. Opening the PR forecloses (1). Decide in that order, not in the order they occur to you.
>
> If it survives into `main` as a live file, it stops being a tracker and becomes a second, unowned
> source of truth that contradicts the ADRs — worse than never having written it down.

> **✅ Item 3 + item 4 landed 2026-08-10 (`cf54df2`), preceded by `c641985`. The scheduled trap was
> real and is now measured, not predicted.** The banner that used to sit here said splitting the
> commit would redden the build. Both states were run rather than reasoned about:
>
> | PITest `excludedClasses` | mutations | killed | gate |
> |---|---|---|---|
> | with `customer.jooq.*` | 278 | 258 (93%) | pass |
> | without | 1603 | 258 (16%) | **BUILD FAILURE** |
>
> 1,325 generated-code mutants, exactly as the "[corrected] PITest exclusions do NOT generalise"
> finding predicted. **The number is in the `cf54df2` commit body deliberately** — it is what stops
> a future reader "tidying" the exclusion away. Do not re-derive it.

> **⚠️ Do not delete this before increment 4 item 13 lands.** It is currently the only record of the
> admission-model reasoning (polarity, tipping-off, FATCA-vs-Reg S, the Q1 counter-argument). The ADR
> that makes it disposable is scheduled inside increment 4, deliberately — see item 13.

> **Revision note.** Reviewed three times on 2026-08-04 (three original claims empirically
> disproved — marked **[corrected]**), once on 2026-08-05 after folding in the alternatives register
> (marked **[ALT-n]**), and again on 2026-08-05 when the two product questions gating increment 3
> were answered (marked **[Q1]** / **[Q2]**).
>
> **✅ Settled 2026-08-05 — increment 3 is no longer gated:**
> **[Q1]** there is no such thing as a primary nationality; `Nationalities` is a `Set`, closed
> (ALT-2) · **[Q2]** licensing keys on nationality **and** residence, and **does** apply to
> corporates — ALT-5 rewritten around an explicit **connecting factor**.
>
> **Settled 2026-08-06, second round:** **FPE stays** — the over-engineering challenge was rejected
> on calibration; the reference class is a serious bank in production, not a v1 (ALT-7) ·
> **admission evaluation is a pure function, not the port impl** — business logic belongs in domain
> or application unless an adapter is *impossible* to avoid (ALT-5).
>
> **Settled 2026-08-06, third round:** **increment 4 item 0 is done** — the B-2 position is recorded
> in `docs/adr/009-pii-retention-and-erasure.md`, which also closes the ADR half of **WP-116**. Item
> 0 was under-scoped here: WP-116's "done when" gates the *whole* infrastructure layer on it, not
> just the schema, and requires a position per substrate (Kafka, outbox, projections, snapshots,
> backups). See *Increment 4 item 0* below for what it changed.
>
> **Settled 2026-08-08, fourth round — increment 4 item 2 landed (`e1f39da`), with three deviations
> this doc has now absorbed:** cross-table cardinality is enforced by **deferred constraint
> triggers**, not left to the recorder as planned (item 2) · `licensed_country` shipped
> **deliberately empty**, which makes the home-jurisdiction question a *functional* blocker rather
> than a seed-quality one (open question 3 below) · PITest excludes the customer adapter **per
> class, not per package**, which falsifies a claim in increment 5 item 7 (item 4).
>
> **✅ Settled 2026-08-10, fifth round — the home jurisdiction is answered, and it is not an EU/UK
> answer.** **Switzerland first, possibly others later** (so multi-jurisdiction must stay cheap, not
> be built now) · **no disclosure of any refusal reason**, unlicensed-market included — the ArchUnit
> rule stands as written and the "confirm with compliance" hedge is withdrawn · **`cdd-years` is a
> first step**; retention will later vary by jurisdiction *and other criteria*, so the evolution must
> stay cheap · **both customer types ship from the start** — extensibility is a design requirement,
> adding a third type must not mean a rewrite · **FPE key: env var for v1, secret store as the
> target state** · **business-rule algorithms do not live in infrastructure, and identity is a
> business rule** — this settles ALT-7's shape question *past* option (b) · **item 8 before item 7**.
>
> ## 🔴🔴 STOP — ADR-009 IS COMMITTED AND NOW PARTLY FALSIFIED BY THE SWITZERLAND ANSWER
>
> ADR-009 has an *Open input* section stating the home jurisdiction "is deliberately **not** a
> blocker for this ADR: the retention period is five years under both the EU and UK regimes, so only
> the citation moves". **That premise is now false**, and two of the ADR's decisions rest on it:
>
> | ADR-009 assumes | Switzerland |
> |---|---|
> | `cdd-years` defaults to **5** (4AMLD Art. 40(1); AMLR Art. 77(3) from 10 Jul 2027) | **10 years** — **Swiss AMLA (GwG, SR 955.0) Art. 7(3)**, from termination of the relationship or completion of the transaction. ⚠️ *Always write "Swiss": `GwG` is also the German Geldwäschegesetz, whose retention period is **5 years** — i.e. exactly the number this row exists to correct.* |
> | Erasure runs on **GDPR Art. 17(3)(b)** — the right is *suspended*, then AML law *mandates* deletion | **revFADP**, where erasure is not a standalone Art. 17-style right: it flows from the right to object / purpose limitation and is **overridable by an overriding private interest** |
>
> **The default value is the easy half — D2 already made it a property, so it is a config change.
> The erasure *reasoning* is the hard half.** ADR-009's D-series argues erasure is a **sequence, not
> a conflict** ("Art. 17(3) suspends the right, then 4AMLD Art. 40(1) 2nd subpara mandates the
> deletion it had blocked") and tells the reader **not** to build a reconciliation mechanism on the
> strength of it. That argument is EU-law-shaped end to end. **⚠️ Whether Swiss law contains an
> equivalent mandatory-deletion-on-expiry duty is NOT verified — I checked the retention period and
> the shape of the FADP erasure right, not the deletion mandate.** Until someone does, the
> "sequence, not conflict" conclusion must be treated as **unproven for our jurisdiction**.
>
> **Also unresolved: FADP may not be the only regime — and "both apply" is NOT the reassuring
> answer it looks like.** GDPR Art. 3(2) reaches a Swiss firm offering services to data subjects in
> the EU, so the likely answer is FADP **and** GDPR. It is tempting to conclude the ADR's reasoning
> therefore survives. **It does not survive unchanged**, and ADR-009's own quoted text is why:
> Art. 17(3)(b) disapplies erasure where processing is necessary *"for compliance with a legal
> obligation which requires processing by **Union or Member State law** to which the controller is
> subject."* **Swiss AMLA is neither Union nor Member State law.** So if GDPR applies in parallel,
> D4's suspension limb loses its stated hook and needs a different one — Art. 17(3)(e) (legal
> claims), or a legitimate-interests analysis under Art. 6(1)(f). That is a *different argument*
> reaching possibly the same outcome, not the same argument still standing.
> **State the question as: if GDPR applies, does the AML suspension survive on a different limb?**
> Asking merely "does GDPR apply?" invites a "yes, both" that quietly leaves D4 unfixed.
>
> **Owed:** an ADR-009 amendment or a superseding ADR-010. Do **not** silently edit ADR-009 — it is
> committed (`7aa538e`), and its EU/UK reasoning is correct *for the jurisdiction it assumed*.
>
> **Open questions that remain — do not treat this doc as fully closed:**
> (1) ~~the B-2 PII/retention position~~ — **closed by ADR-009**, but see the falsification banner
> above · (2) ~~FPE key provenance + the ALT-7 shape choice~~ — **closed 2026-08-10** (env var →
> secret store; shape (c)) · (3) ~~the **home authorisation jurisdiction**~~ — **closed 2026-08-10:
> Switzerland**, with consequences well beyond the seed rows · (4) authz — none exists anywhere
> (increment 5 item 8), **still open**.
>
> **Newly opened by the Switzerland answer, in priority order:**
> (5) **Does Swiss law mandate deletion once the 10-year AMLA clock expires**, the way 4AMLD
> Art. 40(1) 2nd subpara does? ADR-009's central "sequence, not conflict" argument depends on it.
> **Strong lead, not settled law: `FADP Art. 6(4)`** — personal data *"must be destroyed or
> anonymised as soon as it is no longer required for the purpose of processing"*. If that is the
> answer, the conclusion survives but the **shape changes materially**: the deletion mandate moves
> from AML law to **data-protection** law, and the test stops being a bright-line date and becomes
> purpose-based — *"as soon as no longer required"*. **That is operationally expensive**: a purge
> job keyed on `anchor + 10y` implements the EU shape, whereas the Swiss shape treats the 10-year
> AMLA period as *evidence of continuing purpose* rather than as the deletion deadline, and wants a
> purpose assessment per category. Settle before the purge path is designed, not after ·
> (6) **If GDPR applies in parallel via Art. 3(2), does the AML suspension survive on a different
> limb?** (Not "does GDPR apply" — see the banner for why that phrasing hides the problem.) ·
> (7) **What does `licensed_country` actually contain for a Swiss firm?** Switzerland is a **third
> country** to the EU, so there are no passport rows to seed — see ALT-5, where CRD VI Art. 21c now
> answers a question this document previously left open, and answers it **against us** ·
> (8) **Which sanctions regime binds us** — SECO is the Swiss answer, but that needs confirming
> before the `SANCTIONED` rows are seeded.
>
> **ID scheme:** `ALT-n` = decisions from the alternatives register. `B-n` = backlog. These were
> colliding (two different "B2"s, two different "B3"s) — renumbered 2026-08-05.

## Done — increment 1: domain model (`86a4d3a`)
Customer BC **domain layer**, TDD, reviewed ✅. 55 domain tests green; ArchUnit + Modulith green.

- **VOs:** `CustomerNumber` (10-digit + Luhn), `EmailAddress` (pragmatic shape + lowercase canonical), `CountryCode` (ISO 3166-1 via `Locale.getISOCountries()`), `PersonalName` (given/middle?/family).
- **Enums:** `Gender {MALE,FEMALE,UNSPECIFIED}`, `CustomerType {INDIVIDUAL,CORPORATE}`, `CustomerStatus {ONBOARDING,ACTIVE}`.
- **Sealed** `CustomerDetails` → `IndividualDetails` / `CorporateDetails`.
- **Aggregate** `Customer`: `register()` → `ONBOARDING`; `getType()` derived via pattern match.
- 5 domain exceptions; **two-tier** convention (null → `IllegalArgumentException`/400; invariant → domain exception/422; messages never echo the value). Increment 3 adds a **third tier**: row corruption → 500 + alert.

## Done — increment 2: activation (`493acf4` domain, `309734c` mutation gate)
`Customer.activate(Instant occurredAt)`, TDD, reviewed ✅. 60 customer domain tests green;
ArchUnit 17/17 + Modulith green.

- **`boolean activate(Instant)`** — an **in-memory guard** deciding whether to attempt the write,
  as seen in this transaction's snapshot. **Not** the authority on what happened: under concurrency
  both threads get `true`. The persistence layer decides.
- **`activatedAt`** nullable + `Optional<Instant> getActivatedAt()`; assignment inside the
  ONBOARDING arm → "first activation wins" **for sequential retries**.
- **Exhaustive switch expression**, not a statement: a switch *statement* over an enum is **not**
  exhaustiveness-checked (legacy form, no warning even under `-Xlint:all`); an *expression* is —
  verified with `javac --release 25`.
- **Deferred `suspend`/`reinstate`/`close`** — needs the reason axis (B-1) and the cross-BC
  "open accounts?" invariant Seam A doesn't expose.
- **Mutation gate:** `customer.*` added to PITest `<targetClasses>`; the whitelist previously
  excluded the whole BC. Customer 92% (35/38), full run 195/216 (90%).

### Permanent mutation survivors (do not re-litigate)
Two `CustomerNumber.passesLuhn` mutants are **equivalent** — proven:
- `digit > 9` → `>=`: `digit` is always `2 × original`, hence always even, never exactly 9.
- `sum += digit` → `-=`: negates the total, and `−x ≡ 0 (mod 10) ⟺ x ≡ 0 (mod 10)`.

**[added 2026-08-13, from increment 4 item 15]** A third, in
`CustomerApplicationService.activationInstantOf` — `NullReturnValsMutator` on the `orElseThrow`
supplier. **Unreachable, not untested:** the lambda runs only for a customer that is ACTIVE with a
null `activatedAt`, and no construction path produces one. `register` yields ONBOARDING with a null
instant (`Customer:44`), `activate` assigns status and instant in the same arm (`Customer:96-97`),
and `reconstitute` rejects the pair outright (`Customer:57`). Reaching it would need reflection or a
mocked aggregate, both refused. **Do not delete the check to clear the survivor** — the only
alternative to a typed exception there is a bare `orElseThrow()`, i.e. a `NoSuchElementException`
rendered as a 500 with no corruption alert, which is precisely what ALT-8 forbids.

---

## Done — increment 3: persistable domain (`fccaa7e`)
Reconstitution, nationalities and the admission value types. TDD, reviewed 3x. **147 customer +
architecture tests green; ArchUnit 17/17 + Modulith green; `mvn clean install` green (360 tests).**
Mutation: **100 mutants, 98 killed (98%)** — the only survivors are the two proven-equivalent
`passesLuhn` mutants below.

- **`Nationalities`** (`Set<CountryCode>`, min 1 / max 10, defensive copy, explicit null loop),
  **`IndividualDetails`** gains `nationalities` + `countryOfResidence`, **`registeredAt`** required
  by `register`, **`CustomerState` + `reconstitute`**, **`CustomerNumberGenerator`** port,
  **`CustomerIdTest`** (closes B-3).
- **Admission value types:** `Restriction`, `ConnectingFactor`, `AdmissionMatch`,
  `AdmissionDecision` + `AdmittedDecision` / `RefusedDecision`, `AdmissionSubject` +
  `IndividualSubject` / `CorporateSubject`, `AdmissionPolicySnapshot`.

### Deviations from the plan — all deliberate, do not "restore"
- **`CustomerState.activatedAt` is a nullable `Instant`, not `Optional<Instant>`.** A record
  component is a constructor parameter, and `Optional` is a return type only.
  `Customer.getActivatedAt()` still returns one.
- **Sealed implementers live in their own files** with explicit `permits`, matching
  `CustomerDetails` → `IndividualDetails`/`CorporateDetails`. Hence `AdmittedDecision` /
  `RefusedDecision` / `IndividualSubject` / `CorporateSubject` rather than nested records.
- **8th exception, `AdmissionPolicyCorruptException`** — third tier alongside
  `CustomerRowCorruptException`, its own type because the blast radius differs: a corrupt policy row
  refuses **every** registration, so it must route to a different alert.
- **New invariant `activatedAt >= registeredAt`**, in `activate` and `reconstitute`. Needs no clock
  and no time-varying threshold, so unlike the DoB checks it belongs in the aggregate.
  **🔴 The guard sits INSIDE the ONBOARDING arm, not before the switch** — on the ACTIVE arm nothing
  is assigned, so guarding there turns an idempotent retry under clock skew into a hard error. It
  throws `CustomerRowCorruptException`, not `IllegalArgumentException`: `occurredAt` is
  `Clock`-derived, never a request field, and the same invariant already throws that on read. *This
  was reverted once and re-broke both activation tests — if it looks redundant, it is not.*
- **`AdmissionSubject` carries behaviour**: `connections()` (every country it is tied to, indexed by
  factor) and `establishmentFactor()` (what licensing keys on). Keeps increment 5's `evaluate`
  uniform instead of pattern-matching per subject type externally.
- **`RefusedDecision`'s compact constructor sorts, de-duplicates and rejects empty/null.** `List`
  equality is multiplicity-sensitive, so a duplicated match would yield two unequal audit records
  for one evaluation.

### 🔴 PITest `FRECORD` blindness — applies to every future record VO
PITest's default `FRECORD` filter suppresses **every mutant inside a record's canonical
constructor**. The gate added in `309734c` was therefore measuring almost nothing: `Nationalities`,
`CustomerState`, `AdmissionMatch` and `IndividualDetails` each generated **zero** mutants while the
BC still reported a healthy score.

- **Fix, and the convention from here on: validation goes in a `private static` helper** called from
  the compact constructor. PITest *does* mutate those. This took the BC from ~50 mutants to 100.
- **🔴 The convention is recorded HERE and in the pom — never as a comment on the record.** Every
  one of these classes used to carry a two-line "extracted so the mutation gate can see it: PITest's
  FRECORD filter…" comment. **They were all deleted on 2026-08-10 and must not come back.** A
  build tool's behaviour is not something a reader of `Nationalities` or `LoadedCustomer` needs, and
  repeating it per class puts build config in the domain. Comments earn their place by clarifying
  the code or the domain; this clarified neither. *(Residual, accepted: nothing in the code now
  stops someone inlining a helper back into a compact constructor and silently losing its mutants.
  The gate would sag rather than fail. That is what this section is for.)*
- **Do NOT disable the filter** (`-Dfeatures=-FRECORD`): it also un-filters generated
  `equals`/`hashCode`/`toString`, adding ~40 uncovered mutants and dropping the score below the 80%
  gate.
- **Assign the result only when the helper transforms** (`values = validated(values)` returns a
  copy). For a scalar check the assignment is a redundant no-op — use a `void requireX(...)`.
- **`CorporateDetails` still validates inline** and contributes zero mutants. Follow-up.
- When quoting the BC's mutation score, remember the denominator excludes any compact constructor
  still un-extracted.

### 🔴 Known-open, discovered in review — none block increment 4
- **The `Invalid*Exception` family is registered in no handler.** All of them fall to
  `GlobalExceptionHandler`'s catch-all, so a client submitting zero nationalities gets a **500**
  today, not a 422. Fixed by `CustomerExceptionHandler`, increment 5 item 5.
- **No alert routes on either corruption type.** The only 5xx alert is a *rate* alert, which one
  corrupt row a day will never trip. Both exception javadocs say "requires", not "does".
- **`reconstitute` visibility is unenforced** until increment 4 item 11.


## Key decisions

### Settled in increment 1–2
- **Not event-sourced** (state-stored) — see **ALT-1**, which adds the audit mechanism state-storage
  does not give for free.
- **Derived `CustomerType`.** *Storage consequence (see increment 4 item 2):* with per-subtype
  columns the `kind` discriminant **is** the type — no generated column needed.
- **Email** pragmatic, not RFC 5322 — real check is a confirmation email (deferred).
- **Arch rule**: outer layers optional only for BCs in `INCREMENTAL_DOMAIN_ONLY_BCS`. ArchUnit
  `optionalLayer` only permits a layer to be **empty**; direction rules still apply. ✅ **The set is
  empty as of 2026-08-10** — `customer` was removed the moment item 5's ports made
  `customer.application` non-empty. The field stays for the next BC. See item 12.

### [ALT-1] State-stored **plus** a generic `customer_status_transition` table
State-storage is right for 2 states and ~3 transitions, but it does **not** give the audit trail
KYC/AML requires (*who*, *when*, *why*, retained 5 years) — event sourcing does, as a side effect.
One generic append-only table beats a bespoke audit table per transition:

```sql
customer.customer_status_transition (
  customer_id  UUID        NOT NULL REFERENCES customer.customer (id),
  sequence_no  INT         NOT NULL,           -- 1-based per customer; THE aggregate version
  from_status  TEXT        NOT NULL,
  to_status    TEXT        NOT NULL,
  occurred_at  TIMESTAMPTZ NOT NULL,           -- claimed event time (caller-supplied)
  recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),  -- observed write time (DB-supplied)
  actor        TEXT        NOT NULL,           -- 'SYSTEM' or the compliance officer's subject id
  reason       TEXT        NULL,               -- becomes CHECK-mandatory for suspend/close (B-1)
  PRIMARY KEY (customer_id, sequence_no),
  -- [as shipped in V2, absent from this sketch] SIX row-level CHECKs:
  CHECK (sequence_no >= 1),
  CHECK (from_status IN ('ONBOARDING', 'ACTIVE')),           -- the two IN checks are the guard this
  CHECK (to_status   IN ('ONBOARDING', 'ACTIVE')),           --   doc argues for elsewhere: without
  --   them a bad write surfaces at READ time as CustomerStatus.valueOf blowing up inside
  --   reconstitute. Do not drop them when extracting this sketch into the ADR.
  CHECK (from_status <> to_status),                          -- the cheap same-value-twice mistake
  CHECK (sequence_no > 1 OR from_status = 'ONBOARDING'),     -- see the ALT-8 note below: the chain
  --   as a whole is not expressible, but its BASE CASE is — a customer's first transition can only
  --   leave ONBOARDING, since that is the only status register() produces. This is the one end of
  --   the fabricated-row problem a single-row constraint can reach.
  CHECK (actor <> '')
);
-- 🔴 SUPERSEDED by ADR-009 D5 — this line is NOT the enforcement. Kept only to show what was
-- originally proposed; V6 ships the REVOKEs commented out, because the write path runs as the
-- owning superuser and a REVOKE against a non-existent role is either an error or a silent no-op.
-- The real guard is ENABLE ALWAYS triggers. See the bullet below.
REVOKE UPDATE, DELETE ON customer.customer_status_transition FROM <app_role>;
```

- **Both timestamps are required.** `occurred_at` alone is caller-supplied, so backdating is
  undetectable — a regulator reading this log needs the claimed *and* the observed time.
- **🔴 "Append-only must be enforced, not asserted" — the instinct was right, the mechanism was
  wrong. [superseded by ADR-009 D5, landed in `V6`.]** This bullet used to read "the `REVOKE` is one
  line and turns a comment into a constraint". It does not: the app, Flyway and Debezium all connect
  as the bootstrap superuser that **owns** every table, and a superuser bypasses ACLs entirely — so
  the `REVOKE` was documentation wearing a constraint's clothes, which is the exact failure it was
  written to prevent. What ships instead: `BEFORE UPDATE` / `BEFORE DELETE` row triggers **plus** a
  statement-level `BEFORE TRUNCATE` trigger, all `ENABLE ALWAYS` (so `session_replication_role =
  replica` cannot silence them). `V6` ends with a standing rule worth repeating here, because this
  section is where someone will be tempted to re-propose it:
  > **No design in this repository may cite a `GRANT` or `REVOKE` as an enforcement mechanism until
  > the write path stops running as the owning superuser.**
- **This is not event sourcing.** `customer` remains the source of truth; state is never rebuilt
  from this table. It is an audit log that also serves as the concurrency control (**ALT-8**). Say
  this in the ADR — the shapes look similar enough that someone will "finish the job".
- **GDPR erasure will bite on the FK** (B-2). Deleting a customer either blocks on, or cascades
  away, the very record retention requires. Resolve in the B-2 position, not by picking a cascade
  rule now.
- **Lands in increment 4**, not deferred to B-1: activation is the first transition and must write
  the first row, or the log has a hole from day one.

### [ALT-2] ✅ [Q1] `Nationalities` is a `Set` — settled, not deferred
**There is no such thing as a primary nationality**, and modelling one would be a *defect*, not
merely redundancy. **`record Nationalities(Set<CountryCode> values)`, min 1, max 10.** Closed.

- **Nationality is set-valued and unranked; each element is fully effective in its own
  jurisdiction.** Codified by the **Master Nationality Rule** (Hague Convention 1930, Art. 4): a
  state may not afford diplomatic protection to one of its nationals against a state whose
  nationality that person also possesses. International law's answer to "which one counts?" is
  *both, simultaneously, each at home*. The only ranking concept that exists — the **dominant and
  effective nationality** doctrine — is a per-case **adjudicated** determination, not a stored field.
  *(Cite it correctly: the doctrine comes from the Mergé Claim (1955) and Iran–US Claims Tribunal
  Case A/18 (1984), and is **restated at ILC Draft Articles on Diplomatic Protection, Art. 7**
  (2006). **Not Nottebohm** — that case established the separate "genuine link" requirement and did
  not turn on dual nationality. ILC Art. 7 is both accurate and the stronger authority: a general
  default rule rather than one contested judgment. Say **restated**, not "codified" — they are draft
  articles adopted by the Commission and recommended to the General Assembly, not binding law, and
  this is a document whose entire argument is citation precision.)*
- **Every regime we care about keys on something else.** FATCA: *US person* = US citizen **even if
  resident abroad**, or US tax resident, or green-card holder — a **predicate over the set**, not a
  lookup. CRS: **tax residence**, plural and self-certified as a list; nationality is not the CRS key
  at all. Sanctions screening: **all** identifiers, with explicit guidance against inferring from any
  single nationality or residence.
- **🔴 A `primary` field would be a loaded gun pointed at the one check that is legally
  non-negotiable:**
  ```java
  if (primary.equals(US))            // a FR/US dual with primary=FR is SILENTLY MISSED
  if (nationalities.contains(US))    // correct: US-person status ignores ranking entirely
  ```
- **The old trigger is withdrawn.** It is no longer "ask compliance whether a primary is
  distinguished" — the answer is settled *no*. The three things people mean by "primary nationality"
  each have a different home, and none is a field on `Nationalities`:

  | "Primary nationality" usually means | Actually belongs to |
  |---|---|
  | the passport they presented | the **identity document**, not the person — a future document/verification entity. They can present a different passport next year without their nationalities changing |
  | where they are taxed | **tax residence**, plural — B-10 |
  | which one "really counts" | the dominant-and-effective doctrine (ILC Art. 7); a tribunal decides, per case |

  So the real trigger is **a document model landing**, which is a *new entity*, not a field on this
  VO. Do not reopen this as a `Nationalities` shape question.
- **Trigger → role types for countries:** `CountryCode` is the *format* VO (ISO shape + existence),
  genuinely role-independent — nationality, residence and incorporation need identical validation.
  Introduce `Nationality` / `CountryOfResidence` / `CountryOfIncorporation` wrappers **when two
  `CountryCode` components sit adjacent in one constructor**, which happens the moment
  `CorporateDetails` gains a second country. Until then they are ceremony. It is a *when*, not a
  *whether*. **B-13 is that moment** — place of central administration alongside incorporation.
- **[Q2] Corporate has a country but not a nationality — resolved, not merely noted.** A legal entity
  has no nationality and no residence, so ALT-5 gives it its own **connecting factor**
  (`INCORPORATION`) rather than treating it as an unkeyed third axis. The sealed `AdmissionSubject`
  carries the shape; the connecting factor carries the policy. Neither is an overload.

### [ALT-3] Max 10 nationalities, enforced in the VO
Reverses the earlier "input-size guards belong at the DTO boundary" position: the boundary only
protects the HTTP path; a batch importer, a Seam B consumer or a fixture could construct an
unbounded set. One enforcement point on the constructor closes that.

- Invariant → **domain exception → 422**. `maxItems: 10` still goes in the OpenAPI schema —
  **deliberate duplication**: the edge gives a 400 with a field-level message, the VO gives the
  guarantee.
- 10 is arbitrary and that is fine — say so in the exception message so nobody hunts for the rule.

### Settled 2026-08-04 — persistence topology
- **Separate Flyway instance for `customer`.** A single instance means one shared
  `flyway_schema_history`: colliding versions and one ordered migration stream across BCs.
  *Accepted con:* no global cross-schema ordering. Acceptable only because the module graph is
  acyclic by rule.
- **[corrected] A second `Flyway` bean silently disables the first, *and* kills `spring.flyway.*`.**
  `FlywayAutoConfiguration.FlywayConfiguration` carries both `@ConditionalOnMissingBean(Flyway.class)`
  and `@EnableConfigurationProperties(FlywayProperties.class)` on the **nested class**. One user bean
  backs off the auto-configured `account` instance, its `FlywayMigrationInitializer`, **and**
  `FlywayProperties` — every `spring.flyway.*` key goes inert.
  `spring.flyway.enabled` **also stops working**, in the worst way: it gates the auto-configuration,
  not your beans, so `false` leaves migrations running *and* drops
  `@Import(DatabaseInitializationDependencyConfigurer.class)`, breaking DataSource ordering. A kill
  switch that doesn't kill and silently removes an ordering guarantee is worse than none. Either put
  `@ConditionalOnBooleanProperty(name = "spring.flyway.enabled", matchIfMissing = true)` on the user
  config, or delete the key and state migrations always run.
  **Declare both `Flyway` beans and both `FlywayMigrationInitializer` beans**; move schema selection
  **into the bean definitions**, production *and* tests. *(Declaring the initializers is right for
  explicitness, but don't defend it on ordering grounds: `FlywayDatabaseInitializerDetector` already
  detects plain `Flyway` beans. Someone will check.)*
- **Separate jOOQ `<execution>`** → `…customer.jooq`. The ArchUnit `..jooq..` rule generalises free.
- **[corrected] PITest exclusions do NOT generalise** — literal per-BC prefixes. `customer.*` is
  already a target, so the build breaks the moment increment 4 generates jOOQ.
- **Uniqueness is a DB constraint, not an aggregate invariant.** *Enhancement:* pre-check for a
  friendly field-level 400 **and** keep the constraint as the race-proof backstop.

### [ALT-4] `CountryCode` stays JDK-backed for now; own enum eventually
Simplicity wins today. **Three concrete switch triggers:** (a) alpha-3 or numeric codes are needed;
(b) a code the JDK lacks is needed for onboarding (XK/Kosovo); (c) the first JDK upgrade that shifts
the set. Keep the sample-based tripwire test until then — a mitigation, not a fix.

### Settled 2026-08-04 — country validation, split in two
| | **Existence** | **Admission** |
|---|---|---|
| Question | is `FR` a real ISO 3166-1 code? | do we onboard this subject? |
| Changes | ~never (South Sudan, 2011) | weekly (sanctions, licensing) |
| Time-invariant | **yes** | **no** |
| Lives in | `CountryCode` VO, in-process | a port, evaluated by the use case |

- **`CountryCode` stays in-process.** A DB-backed VO constructor makes persisted rows
  **un-rehydratable** the day compliance edits the list: the repository loads a row, calls
  `reconstitute`, the VO throws. History would depend on present-day policy. It also cannot be built
  — the ArchUnit purity rule forbids Spring/jOOQ in `..domain..`. Treat that failure as the design
  talking, not an obstacle.
- **Admission is a policy, not an aggregate invariant.** Evaluated in the use case, before
  `Customer.register(...)`. Never in the aggregate: a factory that can fail with a DB timeout is not
  a domain model.
- **Port name `CountryAdmissionPolicy`** — not `CountryCatalog` (implies the existence question),
  not `…EligibilityPolicy` (`docs/context-map.md` uses "eligibility" for Seam A).
- **Record the decision; never recompute.** `policy_version` **must be read in the same
  query/transaction as the check**. Source is a **list-level** single-row
  `customer.admission_policy(version)`. **Not a per-country column** — that records the version of
  the *matched rows*, and a de-listed country contributes no row, so its version is structurally
  invisible. **Not the Flyway version** — `flyway_schema_history` is Flyway-owned state.
- **Do not build a sanctions list.** Vendor screening API territory (fuzzy name matching, PEP lists,
  daily deltas).

### [ALT-5] ✅ [Q2] Restriction × **connecting factor** — not "blocking vs licensing"
The first draft split admission into blocking (ALL over nationalities ∪ {residence}) and licensing
(membership on **residence only**). It was right to separate the two policies and **wrong about the
licensing key**.

**Licensing keys on nationality too, and the reason is asymmetric.** Most jurisdictions regulate
*where the activity happens*, so their rules key on residence/establishment. **The US is the
outlier:** it asserts personal jurisdiction over its citizens *wherever they live* — one of only two
countries with citizenship-based taxation (Eritrea being the other).

**🔴 Two distinct US regimes, and only one of them justifies the `NATIONALITY` row. Do not merge
them:**

| | keys on | what it costs us |
|---|---|---|
| **FATCA** (IRC §7701(a)(30); green card via §7701(b)(6), the lawful-permanent-resident test) | **citizenship**, wherever resident · US tax residence · green card · US-incorporated entity (§7701(a)(30)(C)) | a **reporting** burden that follows the passport around the world |
| **Reg S** (17 CFR §230.902(k)) | **residence** in the US for natural persons · **incorporation** for entities | offering/solicitation restrictions keyed on where they are |

So the `('US','RESTRICTED_PERSON','NATIONALITY')` row is justified by **FATCA**, not by Reg S — Reg S's
natural-person limb is residence-based and would not produce it. Refusing US persons outright is
standard practice at non-US firms and is more conservative than either regime strictly requires; the
driver is the compliance cost of the FATCA reporting obligation, not a licensing prohibition. An
earlier draft of this section stated the causal story as one general truth about "US person" — it was
wrong, and it was the sentence the `NATIONALITY` seed row rested on.

So the abstraction is **not** "which policy keys on which field". It is that **every restriction
declares its own connecting factor**:

| | `SANCTIONED` | `RESTRICTED_PERSON` | `UNLICENSED` |
|---|---|---|---|
| Example | "RU is sanctioned" | "we do not accept US persons" | "we hold no licence in BR" |
| Polarity | deny-list | deny-list | **allow**-list |
| Connecting factors | nationality · residence · incorporation | nationality · residence · **incorporation** | residence · incorporation |
| Quantifier | ANY hit → refuse | ANY hit → refuse | must be a **member** |
| Changes | weekly (designations) | rarely | on licence grant/loss |

**🔴 `RESTRICTED_PERSON` covers `INCORPORATION` too — a US company *is* a US person.** The first
draft of this rewrite gave it nationality + residence only, which admits a Delaware corporation:
Reg S Rule 902(k)(1)(ii) puts "any partnership or corporation organized or incorporated under the
laws of the United States" inside *U.S. person*, and IRC §7701(a)(30)(C) does the same for FATCA
("a domestic corporation"). A corporate applicant would have been tested against `SANCTIONED` and
`UNLICENSED` and **never against the restricted-person rule at all**.

The original bug the split existed to prevent still cannot occur: a French resident holding the
nationality of a country we merely lack a licence in is **not** refused, because `UNLICENSED` has no
`NATIONALITY` connecting factor. We lack a licence to serve them *there*, and they are *here*.

#### Two tables — polarity structural, connecting factor as data
```sql
-- DENY side: sanctions and restricted-person categories.
customer.restricted_country (
  country_code       CHAR(2) NOT NULL,
  restriction        TEXT    NOT NULL,  -- 'SANCTIONED' | 'RESTRICTED_PERSON'
  connecting_factor  TEXT    NOT NULL,  -- 'NATIONALITY' | 'RESIDENCE' | 'INCORPORATION'
  PRIMARY KEY (country_code, restriction, connecting_factor)
);
-- ALLOW side: where we are authorised, and for whom.
customer.licensed_country (
  country_code       CHAR(2) NOT NULL,
  connecting_factor  TEXT    NOT NULL,  -- 'RESIDENCE' | 'INCORPORATION'
  PRIMARY KEY (country_code, connecting_factor)
);
customer.admission_policy (version)     -- single row, covers BOTH tables
```
The US-person rule is then **three seeded rows and no code**, which is the whole point of the
connecting factor being data rather than a branch:
```
('US', 'RESTRICTED_PERSON', 'NATIONALITY')     -- US citizen, wherever resident
('US', 'RESTRICTED_PERSON', 'RESIDENCE')       -- US-resident individual, whatever their nationality
('US', 'RESTRICTED_PERSON', 'INCORPORATION')   -- US-incorporated entity (Reg S 902(k)(1)(ii))
```
**That third row is the design paying for itself.** The entity limb of the US-person rule was a real
regulatory hole in the first draft, and closing it cost **one seeded row and zero lines of code** —
no new table, no new branch, no new enum value, no migration to the domain model. Under three flat
lists it would have been a new table plus an evaluation branch plus a reason-enum value. That is the
concrete argument for the connecting factor being data; cite it when someone proposes flattening.

- **🔴 Polarity must never become a column.** Licensing as an allow-list fails **closed** (forget a
  country → we refuse business; embarrassing, recoverable). As a deny-list it fails **open** (forget
  a country → we serve a market we hold no licence in; a **regulatory breach**). Sanctions must be a
  deny-list because "all non-sanctioned countries" is not enumerable. A single table with a
  `polarity` column lets one mis-seeded row silently invert a rule; two tables make that
  unrepresentable. This is the same reasoning that keeps `kind` a discriminant rather than a flag.
  - *Pre-empting the obvious objection:* `restriction` **is** a category in a column, which looks
    like the thing this bullet just forbade. It is not the same thing. A mis-seeded `restriction`
    changes only the **audit label** — `SANCTIONED` and `RESTRICTED_PERSON` both deny, so the
    decision is unchanged. A mis-seeded `polarity` **inverts the decision**. Categorise freely
    *within* a polarity; never across one.
- **`licensed_country.connecting_factor` is not ceremony** — it is what says **which field to read**.
  An individual has no country of incorporation and a corporate has no residence, so without the
  factor the allow-list cannot be evaluated against a `Corporate` subject at all. `('FR','RESIDENCE')`
  present with `('FR','INCORPORATION')` absent means we may serve individuals resident in FR but hold
  no permission covering entities established there.
  - *Do not justify this on passporting.* MiFID II Art. 34/35 notifications split on country ×
    **service/activity**, not on customer type, so "individuals but not companies" is not a split any
    passport notification produces. And **CRD VI cuts the other way**: Art. 21c *bans* third-country
    institutions from providing core banking services (deposits, lending, guarantees and commitments)
    cross-border into the EU and requires authorisation per member state, with no passport for
    third-country branches — MiFID-scope investment services and related ancillary services expressly
    carved out. That supports the per-country **allow-list** more strongly than passporting does, but
    via a different mechanism, so cite it for that, not for the factor column.
  - **🔴 [corrected 2026-08-06] The dates note above was wrong in the direction that matters, and
    the grandfathering window has already shut.** "Art. 21c applies from 11 January 2027" is true of
    21c(1)–(4) and (6) only. Dir. (EU) 2024/1619 **Art. 2(1) fourth subparagraph** carves out
    21c(5) — the acquired-rights provision — and makes it applicable from **11 July 2026**. That was
    **26 days ago**. So a contract for Art. 47(1) core banking services concluded from 11 July 2026
    onward gets **no** grandfathering and must already be structured for the regime going live on
    11 January 2027. This stops being "a forward-looking justification, not a current one" — do not
    reuse that sentence. *(Also unresolved in the text itself: the Directive never defines "existing
    contracts", and is silent on drawdowns, rollovers and amendments under a pre-cutoff facility.
    Do not state that scope as settled.)*
- **✅ [ANSWERED 2026-08-10] The home authorisation is SWITZERLAND** (FINMA), with other
  jurisdictions possibly following — so keep multi-jurisdiction *cheap*, do not build it now. This
  bullet used to say the jurisdiction was unnamed and that it decided two things. Both are now
  decided, and **both went the harder way**:
  - **🔴 CRD VI Art. 21c constrains US, not our counterparties.** Switzerland is a **third country**
    to the EU. From **11 January 2027**, a third-country undertaking may not provide **core banking
    services** (deposit-taking, lending, guarantees and commitments) cross-border to EU clients
    without a **licensed branch in the relevant member state** — no passport, per member state.
    Grandfathering covers contracts concluded before **11 July 2026**, a date that has **already
    passed**. The earlier note in this section correctly flagged the acquired-rights carve-out; what
    it could not say, without the jurisdiction, is that we are on the **receiving** end of it.
  - **🔴 There are no EEA passport rows to seed — the "~30 rows" branch of this question is dead.**
    A Swiss firm holds no MiFID II / CRD passport. `licensed_country` is CH plus whatever specific
    cross-border permissions exist, each of which has to be evidenced. **Every row is a compliance
    assertion, not a guess** — which is exactly why `V3` shipped the table empty rather than
    populated on assumption. That instinct is now vindicated, not merely cautious.
  - **🔴 `licensed_country` structurally CANNOT express two permissions a Swiss firm plausibly
    has — record the limit before anyone seeds the table.** The model is country × connecting
    factor. Both of the following are real and neither fits that shape:
    - **CRD VI Art. 21c has exemptions that are per-application facts, not country properties** —
      notably **reverse solicitation** (the client's own exclusive initiative), plus interbank,
      intragroup and the MiFID carve-out. "May we serve this person?" then depends on *how the
      relationship started*, which no row in this table can encode.
    - **The Berne Financial Services Agreement (UK–Switzerland) is in force since 1 Jan 2026** and
      grants Swiss firms UK market access — but only for **wholesale / institutional / certain
      high-net-worth** clients. A `('GB','RESIDENCE')` row would be true *only for a client segment
      the model has no field for*. Seed it unconditionally and we have quietly opened UK **retail**
      business. *(Confirm the exact scope with compliance before any GB row — cited here to show
      the shape of the problem, not as a settled permission.)*

    Neither breaks the design; both mean the allow-list models a **strict subset** of reality. Write
    the limit into the ADR: *`licensed_country` expresses country-level permissions only;
    segment-conditioned and per-client-conditioned permissions are out of scope and must never be
    seeded as unconditional rows.*
  - **What survives unchanged:** the Reg S / FATCA analysis, which turned only on our being a
    **non-US** firm. Switzerland is non-US, so the US-person triple stands exactly as seeded.
  - *Adjacent, and it changes a reporting channel rather than the admission model:* Switzerland's
    FATCA IGA converts from **Model 2 to Model 1 effective 1 January 2027** — reporting will run
    through the Swiss FTA rather than direct-to-IRS. B-12's gap analysis is unaffected in substance;
    the citation is not.
  - **🔴 Swiss tipping-off citations — and get the DIRECTION right, because one obvious candidate
    runs the wrong way.** An earlier draft of this bullet cited **Banking Act (BankG, SR 952.0)
    Art. 47** as a tipping-off basis. **It is not one.** Art. 47 criminalises disclosure of client
    information *entrusted to the bank* by its bodies, employees, agents, auditors or liquidators
    **to third parties** — it protects the client's secrets *from outsiders*. The rule we are
    justifying is the opposite vector: withholding the *reason* for a refusal **from the applicant
    themselves**. Art. 47 says nothing about that. Citing it in the ADR would be caught immediately
    by a compliance reader, and would put the credibility of the correct parts of this section in
    question too.
    - **For tipping-off: `AMLA Art. 10a`** — but scope it honestly (next bullet).
    - **Art. 47 is still load-bearing, just elsewhere:** it is a real constraint on ADR-009 **D7**
      (personal data reaching operators, vendors or outsourced processors through logs, metric
      labels and exception messages) and on any cross-border data flow. Cite it *there*.
  - **🔴 After the "no disclosure at all" answer, the doc carries TWO rules under one label — say
    so, or it over-claims criminal liability.** AMLA Art. 10a's information ban attaches to a filed
    MROS report, and in practice from the moment suspicion forms. It does **not** make silence a
    criminal-law duty for a refusal that fires on `UNLICENSED`, where there is no suspicion and no
    report:

    | Refusal | Basis for withholding the reason |
    |---|---|
    | `SANCTIONED` / `RESTRICTED_PERSON` where suspicion forms | **criminal-law duty** — AMLA Art. 10a. A compile-time ArchUnit guarantee is proportionate. |
    | `UNLICENSED` | **firm policy** — decided 2026-08-10, correctly. Not criminal liability. |

    Same code, same generic 422 — the difference is what the ADR may claim. Do not let this
    document be cited as "disclosing a licensing refusal is a crime in Switzerland".
  - **🔴 MISSED CONSEQUENCE — under AMLA Art. 9(1)(b) a refusal can be the START of a regulatory
    obligation, not the end of the interaction.** Verified: a financial intermediary that **breaks
    off negotiations to establish a business relationship** on the basis of reasonable suspicion
    **must immediately report to MROS**. The flow as designed refuses, writes `admission_decision`,
    returns a generic 422, and stops. Three consequences:
    1. The refusal path needs a route into the MROS process. Out of scope for v1 *code*; **in scope
       for the item 13 ADR**, which must not read as "refusal is terminal".
    2. It supplies the missing Swiss hook for the table above — a reported refusal is exactly the
       branch where Art. 10a's criminal ban genuinely bites.
    3. It splits the retention profile: a refusal on which a report was filed is not the same record
       class as an `UNLICENSED` refusal. A second, independent reason `cdd-years` cannot stay one
       scalar.
- **`IndividualDetails` gains `countryOfResidence: CountryCode`** (singular — one primary residence;
  *tax* residence can be plural and is B-10). Lands in **increment 3**, same migration-economics
  argument that already won for `nationalities` and `registeredAt`. Without it neither `UNLICENSED`
  nor the residence limb of `RESTRICTED_PERSON` can be evaluated, and it pre-empts the "overload
  nationality to mean residence" bug.

#### Port shape — unchanged by Q2, deliberately
- **The port takes a sealed subject, not overloads.** Two overloads distinguished only by a
  `CountryCode` argument invite exactly the positional swap that `CustomerState` exists to prevent —
  and a swapped residence/incorporation yields a *plausible* decision rather than a crash.
  ```java
  sealed interface AdmissionSubject {
  // Implementers live in their OWN files (standard Java), with an explicit permits clause:
  //   record IndividualSubject(Nationalities nationalities, CountryCode residence)
  //   record CorporateSubject(CountryCode countryOfIncorporation)
  }
  AdmissionDecision evaluate(AdmissionSubject subject);
  ```
  Adding a third subject then breaks compilation at every evaluation site — the compiler enforcing
  what would otherwise be a note. **Q2 changed the *evaluation*, not the subject shape** — which is
  the correct blast radius, and evidence the seam was drawn in the right place.

> **✅ SETTLED 2026-08-06 — the quantifier logic is a pure function, NOT the port impl.** Three
> statements in this document had pointed three ways (use case / port method / db adapter). The rule
> is: **business logic lives in the domain or application layer; an adapter hosts it only if that is
> *impossible* otherwise.** One round trip is convenience, not impossibility.
>
> ```java
> // PORT — loads, does not decide.
> interface CountryAdmissionPolicy { AdmissionPolicySnapshot load(); }
>
> // PURE FUNCTION — domain/application. Deny-ANY, allow-membership, ordering, Refused assembly.
> AdmissionDecision evaluate(AdmissionSubject subject, AdmissionPolicySnapshot policy);
> ```
>
> `AdmissionPolicySnapshot` carries both tables **and** `policy_version` read in the same statement,
> so the single round trip and the version-consistency guarantee both survive unchanged. ALT-5's
> positional-swap argument also survives untouched — it was always about the `evaluate` *signature*,
> never about which layer hosts it.
>
> **What the adapter-hosted version would have cost, and why "one round trip" did not pay for it:**
> 1. **The most compliance-critical branch logic in the BC would sit outside the mutation gate.**
>    Increment 4 item 4 adds `customer.infrastructure.*` to `<excludedClasses>` — a convention that is
>    safe only because adapters are dumb. Real branch logic there silently leaves the safety net.
> 2. **Increment 5 item 6's admission assertions would have tested a stub.** With
>    `CountryAdmissionPolicy` mocked, "deny-ANY vs allow-membership" and the US-national-FR-resident
>    case assert the mock's configured return. *That signature — a test asserting what you told the
>    mock to return — is the tell that logic is in the wrong layer.*
>
> **Corollary: no SQL-side matching.** Expressing the quantifiers as a `WHERE` clause moves them
> further out than a Java adapter does and is easy to wave through in review because it looks like
> "just a query". `load()` returns rows; Java decides. This also removes the "same hazard if the
> adapter matches in SQL without `ORDER BY`" caveat noted above — there is no such path now.

#### `AdmissionDecision` is sealed; the flat reason enum is **dropped**
```java
// Declaration order IS the sort order. Most serious FIRST.
public enum Restriction      { SANCTIONED, RESTRICTED_PERSON, UNLICENSED }   // genuine severity
public enum ConnectingFactor { NATIONALITY, RESIDENCE, INCORPORATION }       // arbitrary, but FROZEN

public record AdmissionMatch(Restriction restriction, ConnectingFactor connectingFactor,
                             CountryCode triggeringCountry) {}

public sealed interface AdmissionDecision {
  long policyVersion();

  record AdmittedDecision(long policyVersion) implements AdmissionDecision {}   // own file

  record RefusedDecision(List<AdmissionMatch> matches, long policyVersion)   // own file
      implements AdmissionDecision {

    private static final Comparator<AdmissionMatch> MOST_SERIOUS_FIRST =
        Comparator.comparing(AdmissionMatch::restriction)             // enum ordinal, ascending
                  .thenComparing(AdmissionMatch::connectingFactor)
                  .thenComparing(m -> m.triggeringCountry().value()); // CountryCode is NOT Comparable

    public Refused {
      if (matches == null || matches.isEmpty()) {
        throw new IllegalStateException("a refusal must carry at least one match");
      }
      for (AdmissionMatch m : matches) {                  // NOT redundant - see the null-element note
        if (m == null) throw new IllegalStateException("a refusal match must not be null");
      }
      matches = matches.stream().sorted(MOST_SERIOUS_FIRST).toList();  // sorts AND copies
    }

    /** The most serious match: the HEAD of the ascending order, i.e. the minimum. */
    public AdmissionMatch primary() { return matches.getFirst(); }
  }
}
```
This replaces `ADMITTED / BLOCKED_NATIONALITY / BLOCKED_RESIDENCE / BLOCKED_INCORPORATION /
UNLICENSED_MARKET`. Three reasons, in order of weight:

1. **It defuses "a wrong enum is a migration."** A flat reason enum is the cross-product
   restriction × connecting factor — 9 reachable values under Q2's answers, and every new connecting
   factor multiplies it. Two small orthogonal enums do not grow that way.
2. **It records *which country* triggered the refusal.** `BLOCKED_NATIONALITY` throws that away. Five
   years on, the audit row must read `REFUSED / SANCTIONED / NATIONALITY / RU / v42`, not "blocked on
   some nationality" — the investigator's first question is *which one*.
3. **No `Optional` components.** The sealed split makes admitted and refused structurally distinct,
   and the use case pattern-matches exhaustively instead of unwrapping four optionals that are
   all-present-or-all-absent by convention.

**🔴 `Refused` carries *all* matches, not one — and the list is sorted.** An earlier draft of this
section had a single `(restriction, connectingFactor, matchedCountry)` triple, which is wrong twice:

- **The deny quantifier is ANY over a set, so multiple rules fire routinely.** A dual FR/RU national
  resident in IR hits `SANCTIONED` **twice** (RU/NATIONALITY and IR/RESIDENCE). A single triple
  silently discards one.
- **The tie-break would have been `Set` iteration order.** The "unordered in the model, ordered on the
  wire" decision already establishes that `Set.copyOf` randomises per JVM run and mandates *sort at
  every serialisation boundary — the domain→DTO mapper, the ALT-9 fingerprint, `admission_decision`,
  any export*. That rule was written and then **not applied to the evaluation that produces the
  audit row**. Two identical retries of the same refused registration would have written **different**
  `matched_country` values into a 5-year-retention compliance table — and the section above says the
  investigator's first question is *which one*. "Whichever the JVM salt picked that morning" is not an
  answer. *(Same hazard if the adapter matches in SQL without an explicit `ORDER BY`.)*

#### The total order is explicit, enforced in the constructor, and part of the contract
Matches sort **most serious first**: by `restriction`, then `connectingFactor`, then
`triggeringCountry`, all ascending by enum ordinal / string. `SANCTIONED` → `RESTRICTED_PERSON` →
`UNLICENSED`. An applicant who is both sanctioned and in an unlicensed market produces a *sanctions*
audit record, not a licensing one.

**🔴 `primary()` is the MINIMUM under that order — the head — not the supremum.** An earlier draft of
this section said "supremum", which is exactly backwards: `SANCTIONED` is declared *first*, so
ascending order makes it the least element. An implementer reading "supremum" writes
`Collections.max(...)` or `getLast()`, gets a green build, and labels every multi-match refusal
`UNLICENSED` — mislabelling five years of a retention-bound audit table with no test failing. Say
**head**, or **most-serious-first**; never "supremum".

- **Enum declaration order is load-bearing and must not be reordered for tidiness.** `Restriction`'s
  order is genuine severity. `ConnectingFactor`'s is **arbitrary but frozen** — there is no severity
  relation between nationality, residence and incorporation; it exists only as a deterministic
  tie-break. Label it that way in the code, or someone "corrects" it on severity grounds and silently
  changes which match is primary in every historical row.
- **🔴 Sortedness and non-emptiness are constructor invariants, not comments.** An earlier draft
  asserted both in javadoc and delegated enforcement to prose in two other places ("sort on write",
  "`ORDER BY` in the adapter") — which is the same distributed-convention arrangement whose failure
  produced this finding in the first place. Three concrete defects it left open, each already
  condemned by name elsewhere in this document: records **do not defensively copy** (the
  `Nationalities` rule), an unsorted list was accepted silently, and `getFirst()` on an empty list
  throws `NoSuchElementException`. The compact constructor closes all three. Once it does, "sort on
  write" and the SQL `ORDER BY` become belt-and-braces rather than the guarantee.
  - **Scope that third one honestly:** the constructor moves the failure from *read time* to
    *construction time*, which is the real win. It does **not** improve alerting —
    `IllegalStateException` is as untyped as `NoSuchElementException` and lands in the same catch-all
    500 with nobody paged. If the third tier's dedicated type (item 5) should cover this, say so;
    otherwise do not claim the bare-`orElseThrow` rule is satisfied.
  - **🔴 The explicit null-element loop is NOT redundant with the sort.** Verified: a **1-element**
    list `[null]` survives `.stream().sorted(cmp).toList()` untouched, because a single-element sort
    never invokes the comparator — so `primary()` returns `null`. At size ≥2 it NPEs. This is the
    identical trap `Nationalities` needs a null loop for; the four
    ordering tests below cannot see it, so it needs its own.
- *Compile detail worth keeping:* `CountryCode` is a `record CountryCode(String value)` and does
  **not** implement `Comparable`, so the third comparator key must go through `.value()`.
- *Tier note:* an empty `Refused` is a **code defect**, not client input — it must not land in tier 1
  (`IllegalArgumentException` → 400). `IllegalStateException` above; revisit if the third tier grows a
  dedicated type.

**The property the order buys is stability under rule addition**, not "monotone severity" — an
earlier draft said the latter and hung it on B-1's `CHECK`, which is a different axis entirely (B-1's
`reason` is the *transition* reason; see the backlog note). Because the primary is the **head** under
a **fixed** order, seeding a new `UNLICENSED` row can never displace a previously-recorded sanctions
refusal. Historical `admission_decision` rows therefore stay comparable across policy versions —
which is the whole point of retaining them.

#### ⚠️ AML tipping-off — now enforced, not asserted
In most jurisdictions, telling a customer they matched a sanctions or AML check is a **criminal
offence**, not merely bad practice. The 422 body is generic (*"we are unable to open an account at
this time"*); the specifics go **only** to `admission_decision` and the audit log.

The sealed shape turns this from a comment into a build failure — **ArchUnit rule, increment 5 item
9**, landing with the controllers:

> `RefusedDecision` may not be referenced from `..infrastructure.web..`.

The web layer sees the outcome and nothing else. For a criminal-liability rule, a compile-time
guarantee is proportionate.

**✅ [SETTLED 2026-08-10] No disclosure of any refusal reason — unlicensed-market included.** The
earlier hedge ("unlicensed-market is arguably safe to disclose — confirm with compliance") is
**withdrawn**: the answer came back *no disclosure*. The rule as written already blocks every
refusal reason, so **nothing changes in the code** — but the hedge itself must go, because it was an
open invitation to weaken the rule later on the strength of an argument that has now been rejected.
Do not re-derive it from first principles; it was asked and answered.

**🔴 That rule closes the *type* channel. The realistic leak is the *message* channel.** ArchUnit
constrains static type references, so the rule stays green while the use case throws a refusal
exception whose **message** reaches the response body — which is exactly what
`GlobalExceptionHandler.handleException` and `AccountExceptionHandler.handleInternalServerErrorException`
do today (both flagged in increment 5 item 5). `"refused: SANCTIONED/NATIONALITY/RU"` in a 500 body
is a tipping-off disclosure that no type rule can see. So, explicitly:

> **The refusal exception carries the `Refused` record as a private field for the recorder, and a
> compile-time constant as its `getMessage()`.** Never interpolate a match into the message.

This extends the increment-1 convention ("messages never echo the value") from VO invariants to
admission refusals. Stating it here rather than only in increment 5's mapping bullet is deliberate:
a compile-time guarantee covering one of two channels invites more confidence than it earns, and the
one it misses is the one that actually ships bytes to the client.

**🔴 There is a third path, and it is not ours to author: the catch-all handler.** Even with a
constant message on our own exception, a **driver** exception carries the match. The step-3 decision
write is the one statement in the flow whose bind values *are* the sanctions match; pgjdbc's
`logServerErrorDetail` defaults to `true`, so a constraint violation on `admission_decision_match`
puts `Failing row contains (…)` into the message, jOOQ wraps it, and `GlobalExceptionHandler`'s
`@ExceptionHandler(Exception.class)` returns `e.getMessage()` in the body. Fixing that handler is
scheduled in increment 5 item 5 — it is one line of existing code, and it is the difference between
a rule and a wish.

#### Scope honestly — two gaps this does NOT close
- **B-11 — corporate sanctions screening needs UBO data we do not have.** OFAC's **50 Percent Rule**
  blocks entities owned ≥50% **in aggregate**, directly *or indirectly*, by blocked persons — and
  **OFAC publishes no list of them**. Country of incorporation is necessary and nowhere near
  sufficient. *(Precision, and it is regime-specific: the **ownership, not control** distinction holds
  **for OFAC only**. The EU's 2024 Best Practices guidance and UK OFSI both apply an ownership **or
  control** test — de facto dominant influence, or a designated person reasonably expected to be able
  to direct the entity's affairs. For a non-US firm the EU/UK test is the binding one, so do not
  under-scope the UBO gap on the strength of the OFAC carve-out.)*
- **B-12 — "US person" is broader than the three country limbs.** The seeded rows cover citizenship,
  residence and incorporation. **Green-card holders** (a status, not a country field — the model has
  nowhere to put it) and the Model IGA Annex I **indicia** (US place of birth, address, phone,
  standing payment instructions) are not covered. **This document must not be citable as "US person
  handled".**

### [ALT-6] `customer` schema, direct query, no cache
- **`customer` schema, not a `reference` schema and not a separate database.** "Which countries do we
  onboard from" is Customer-context policy, not shared reference data. Schema isolation already
  gives extraction (`pg_dump -n customer`). A separate physical DB costs a second `DataSource` +
  `@Primary` disambiguation, a second Hikari pool, a second Flyway, a second `DSLContext`, and a new
  synchronous failure mode — for a ~250-row lookup with no scaling, isolation or regulatory driver.
  *Trigger to revisit:* a **second** context needs country data. Then extract a `reference` module —
  only the adapter changes. It would need a `reference.infrastructure.db` package (to stay inside
  `..jooq..` confinement) and **no** `reference.domain` package, so BC auto-discovery correctly skips
  hexagonal layering on a CRUD module.
- **RESOLVED: direct in-transaction query, no cache.** The earlier in-memory set was justified by
  "DB downtime degrades refresh, not onboarding" — **that reasoning was wrong**, inherited from the
  separate-database design and not re-derived once the data moved into the `customer` schema. Same
  Postgres as the registration `INSERT`: if it is down, onboarding is down. Zero staleness, no
  scheduler, no gauge, no alert threshold, no fail-fast boot — and correct *at the instant of
  registration*. Registration is not a hot path; a cache here is premature optimization.
  *If load ever justifies revisiting:* Caffeine with a **30–60s** TTL, not a scheduled reload — the
  refresh interval is a **compliance** parameter, since an OFAC designation is effective immediately.
  Redis stays rejected: it buys cross-instance invalidation, not memory.
- **⚠️ Dropping the cache removed the fail-fast boot, which was doing real work.** With
  `licensed_country` empty or mis-seeded, membership **refuses every applicant** — and the
  tipping-off rule guarantees the client-facing signal is a deliberately uninformative generic 422.
  On-call would see registrations go to zero with no error rate, no exception, no log line naming a
  cause. Cheap replacement, **required in increment 4**:
  1. A counter on admission outcomes tagged by **`restriction` + `connecting_factor` + subject
     type** — all low-cardinality and not PII, so safe under B-2's rule (unlike the evaluated
     inputs, and unlike `triggering_country`, which is **PII-adjacent and must stay out of labels**).
  2. An alert on refusal ratio, **partitioned by subject type** — see the next bullet for why an
     aggregate alert is not enough.
  3. A startup assertion, per table, against its own **declared** expected set — plus a targeted
     seed-integrity check on the US triple (see the granularity note below).
- **🔴 [Q2] The failure mode is now *partitioned*, which makes the naive assertion insufficient.**
  Since licensing applies to corporates on `INCORPORATION`, a `licensed_country` seeded with only
  `RESIDENCE` rows **refuses every corporate registration while individuals keep working perfectly**.
  A global "table is non-empty" assertion passes, and an aggregate refusal-ratio alert may never fire
  if corporates are the minority of traffic. Hence the per-factor assertion and the subject-type
  partition above — both are consequences of the Q2 answer, not gold-plating.
- **🔴 "Per factor in use" must be *declared*, not inferred — inferring it is circular.** If the
  expected factor set is read from the tables themselves, an empty `INCORPORATION` set is
  self-consistent and the assertion passes on exactly the outage it exists to catch. If it is
  hardcoded from the enum, it always demands all three, and a firm that genuinely serves no
  corporates yet cannot boot. So it is **configuration**:
  **🔴 One property per table, not one flat list.** The two tables have different admissible domains
  — `licensed_country.connecting_factor` is constrained to `RESIDENCE | INCORPORATION`, so a single
  `NATIONALITY,RESIDENCE,INCORPORATION` set asserted against both is **unsatisfiable and the app
  cannot boot**. The tempting fix is the dangerous one: narrowing the shared list to
  `RESIDENCE,INCORPORATION` makes it boot, and then a `restricted_country` that has lost all its
  `NATIONALITY` rows — including the FATCA row this whole section turns on — passes silently. That is
  the assertion failing on exactly the outage it exists to catch, which is the argument used above to
  reject inference.
  ```properties
  customer.admission.expected-factors.restricted=NATIONALITY,RESIDENCE,INCORPORATION
  customer.admission.expected-factors.licensed=RESIDENCE,INCORPORATION
  ```
  **✅ [SETTLED 2026-08-10] Both factors on the licensed side — corporates are in scope from day
  one.** The question was "do we onboard corporates in v1?", and the answer was: *not certain
  commercially, but the design must carry both types from the start so that adding a third is cheap
  and does not mean a rewrite.* So `licensed=RESIDENCE,INCORPORATION` as written above — **not**
  narrowed to `RESIDENCE`. Consequence to be aware of rather than surprised by: the startup
  assertion will now demand `INCORPORATION` rows, so a `licensed_country` seeded for individuals
  only **fails the boot** instead of silently refusing every corporate. That is the intended
  behaviour — it is the partitioned-outage scenario this property exists to catch — but it means
  the seed migration must cover both factors, or explicitly narrow this property with a sign-off.

  **🔴 And that intended failure lands at the WRONG TIME under the 2026-08-10 ordering — resolve it
  before writing item 8.** `licensed_country` is empty **today**; item 8 now runs **before** item 7;
  and the seed cannot land until the home-jurisdiction work at **item 13**. So the assertion arrives
  roughly five items ahead of the data it asserts on, and on the day it lands it **bricks the boot**
  — including every `@SpringBootTest` context (`AccountOutboxConsumerTest` is the one that exists
  today). Item 8's whole justification is *make the invisible outage visible*; discovering this at
  boot instead would be the same class of surprise. **Pick one and write it down:**
  1. seed a **CH-only `licensed_country`** in the same commit as item 8 (smallest true seed — we are
     authorised in Switzerland, that row needs no further research); or
  2. default `expected-factors.*` to **empty** and make the seed migration set them, so the
     assertion is inert until there is something to assert; or
  3. move the assertion itself behind item 13.

  *(1) is the lean — it makes the assertion live immediately against real data, and it is the one
  row the jurisdiction answer already justifies.*
  Checked against the tables at startup. That also gives deliberate absence an explicit, reviewable
  home — "we do not onboard corporates yet" becomes a config line someone signed off on, rather than
  an empty table nobody noticed.
- **🔴 Granularity: the factor assertion cannot see the loss of the row it was justified by.**
  `expected-factors.restricted` asserts each **factor** appears *somewhere* in `restricted_country`.
  Lose `('US','RESTRICTED_PERSON','NATIONALITY')` in a re-seed and `NATIONALITY` is still covered by
  any `SANCTIONED` row — the app boots green, and US citizens resident abroad onboard cleanly with
  the FATCA obligation silently unmet. The per-table split fixes the *all-rows* case and leaves the
  *one-row* case open, and the one-row case is the realistic seed regression: ALT-5 says the
  US-person rule **is** three rows, and B-12 says those three rows are the whole of it.
  **Add a named seed-integrity assertion on the US triple**, cheaper than making the expected set
  pair-valued and better in an audit — the three load-bearing rows get named in code:
  ```
  assert restricted_country ⊇ {('US','RESTRICTED_PERSON',f) : f ∈ {NATIONALITY, RESIDENCE, INCORPORATION}}
  ```
  *General lesson worth carrying:* check a guard's **granularity** against the specific invariant its
  own justification names, not against the granularity it happens to be written at.
- **Seed both tables in the Flyway migration.** Versioned, reviewed, deployable, free audit trail for
  "who removed RU and when". *Accepted con:* changing a list needs a deploy, which for sanctions is
  arguably too slow — that is the argument for an admin UI later, not for a cache.

#### 🔴 Three standing rules `V3` establishes — added 2026-08-08, absent from every earlier revision
These are the load-bearing consequences of "seed it in the migration", and they were nowhere in this
plan. All three belong in the item 13 ADR.

1. **Version-bump convention.** *Any* later migration that adds, removes or changes a row in either
   policy table **bumps `admission_policy.version` in the same migration.** A policy change that
   leaves the version untouched makes every historical `admission_decision` row claim a policy it
   was not evaluated against. Backed in the schema by `assert_policy_version_advances` (`BEFORE
   UPDATE`, `ENABLE ALWAYS`) — and this is *why* `V6` guards `admission_policy` against
   `DELETE`/`TRUNCATE`: a delete-then-reinsert walks the version backwards without ever firing an
   `UPDATE`.
2. **🔴 The admin UI has a hard prerequisite, and this document previously pointed straight at the
   change that destroys the evidence chain.** A refused decision is self-contained (its match rows
   record which rules fired). An **admitted** one is not: its evidence is "screened clean against
   version N", it carries no match rows, and **version N's content *is* the seed migrations up to
   the one that set N**. That reconstruction lives outside the database, deliberately — which is why
   no in-database policy snapshot table exists. **The moment any path edits these tables outside a
   migration — precisely the admin UI the bullet above recommends — the reconstruction is gone.**
   So that change **must** ship with an append-only snapshot of each version's rules. Not
   sequenced afterwards: without it, an admitted customer's screening evidence becomes
   unrecoverable.
3. **The two policy tables are deliberately NOT write-guarded — do not "complete the set".** Item 2
   now lists three *guarded* non-audit tables, which makes guarding these two look like an
   oversight. It is not, and `V3` states the residual plainly: a direct `DELETE`/`TRUNCATE` from any
   psql session silently falsifies the reconstruction above for every admitted decision, with no
   trace. Left unguarded for two reasons — a guard would force every future de-listing migration
   through the `DISABLE TRIGGER` idiom, and it would be removed anyway by the admin-UI change that
   owns the snapshot table. **The exposure is bounded today only because `licensed_country` is
   empty**, so nobody can be wrongly admitted.
   - **🔴 `V3` says "revisit with the snapshot table, not before" — that trigger is mis-sequenced,
     and the correction is owed to `V3`'s header too.** The bound expires at the **first `ADMITTED`
     decision**, which becomes possible the moment `licensed_country` is seeded — i.e. the very
     commit that answers open question 3 and unblocks the BC, scheduled at item 13. The snapshot
     table and the admin UI are nowhere on the plan. So the real trigger is **before the first
     production admission**, which is far earlier than `V3` implies.
   - *(Item 8's **runtime** US-triple assertion is what would catch this class of out-of-band edit.
     What exists today is the **CI** half — `CustomerSchemaConstraintsTest.the_us_person_rule_is_
     seeded_as_three_rows`, a Testcontainers test. `V3` contains **no `DO` block at all**, unlike
     `V6` and `V18`, so the seed has **zero** production-time coverage at any point in its life —
     not "half covered".)*

### [ALT-7] `CustomerNumber` via format-preserving encryption over the sequence

> **✅ CONFIRMED 2026-08-06 — FPE stays. The over-engineering challenge was raised and rejected; do
> not reopen it.** A review argued FPE was disproportionate: five open problems (new crypto
> dependency with its own approval path, key provenance with no secret store, rotation collision
> semantics, a cycle-walk termination proof, an ArchUnit carve-out forcing a new port shape) to buy a
> threat model scoped as "prevents enumeration and business-intel leakage. **Not authorization**" —
> and recommended random 9-digit + the existing `UNIQUE` constraint instead.
>
> **Rejected on calibration.** The reference class for this codebase is a **serious bank running in
> production**, not a v1 that hardens later. Unguessable customer identifiers are baseline at that
> reference class, not luxury, and "simpler would do for now" is not an argument against a design
> a real institution would ship. Record the alternative honestly — random + `UNIQUE` *does* meet the
> stated threat model, at ~10⁻³ per-insert retry probability at 10⁶ customers in a 10⁹ space — and
> then note that guaranteed **per-key collision-freedom** and a reproducible sequence→number mapping
> are what we are buying, and that we accept the key-custody cost to get them.
>
> **✅ Closed 2026-08-10.** Key **provenance and custody**: env var for v1, secret store as the
> target state (see the key bullet below). Shape: **(c)** — the minting *rule* in the domain, the
> sequence and the cipher behind ports. Sequencing: **item 8 runs before item 7**, which resolves
> the circularity this banner flagged (the generator was scheduled inside increment 4 while its own
> blocker was called a prerequisite for that same item).

Sequence + Luhn is collision-free but **enumerable**: two numbers disclose customer count and signup
ordering. Customer numbers appear on statements and in support calls, so they are effectively public.

```
seq  = nextval('customer.customer_number_seq')   -- 1 .. 999_999_999  (START > 0; 0 is excluded)
body = FPE_k(seq)
while (body == 0) body = FPE_k(body)             -- cycle walk: a LOOP, not one re-encryption
number = zeroPad(body, 9) + luhnCheckDigit(body)
```

- **`START > 0` is what makes the excluded point unreachable**, and it is the load-bearing half.
  The walk is a bijection on `D' = [1, 10⁹)` only if the *input* is already in `D'`. The migration
  sketch and this pseudo-code must agree — an earlier draft said `0 .. 999_999_999` here and
  `START > 0` there, and it is the pseudo-code someone implements.
  *(Given `START > 0`, the loop provably executes at most once: by injectivity, if `F(0) = 0` then no
  `seq ∈ D'` maps to 0 and the body never runs; otherwise it terminates in one step. Keep the `while`
  anyway — it is the general cycle-walking construction and stays correct if the excluded set ever
  grows. Do **not** "simplify" it to an `if` on the grounds that one step suffices; that reasoning
  silently depends on the excluded set being a single point.)*
- **Prefer Bouncy Castle FF1** (`org.bouncycastle.crypto.fpe.FPEFF1Engine`, NIST SP 800-38G, radix 10,
  length 9; `BasicAlphabetMapper` for char↔byte). **This is a new dependency** — BC is not in
  `pom.xml` today, and in a regulated codebase adding a crypto provider has its own approval path.
  Budget for that rather than treating it as a footnote.
  *No-dependency fallback:* a Feistel network with an HMAC-SHA256 round function over the 10⁵/10⁴
  split — **10 rounds, matching FF1**, not 6. Record explicitly that it is not a proven PRP.
- **Rejected: multiply by a constant coprime to 10⁹.** Bijective but affine — three numbers recover
  the mapping.
- **Threat model:** prevents enumeration and business-intel leakage. **Not authorization.**
  `GET /customers/{id}` keys on the UUID and must be authorized regardless.
- **✅ [SETTLED 2026-08-10] Key provenance: env var for v1, secret store as the target state.**
  Not "env var and we'll see" — the secret store is the intended destination, so build the v1 in the
  shape that migrates cheaply: **the key arrives through one seam** (a single provider/supplier
  resolved at startup), never read from `@Value` at multiple call sites, and **never** committed to
  a config file. Moving to a secret store should then change one implementation, not the generator.
  - **The key must be recoverable, and this is the part people underestimate.** Losing it does not
    corrupt existing numbers — they are persisted, never recomputed — but it makes the
    sequence→number mapping **unreproducible**, which matters the day a reconciliation job has to
    verify issuance. Treat key backup as a launch requirement, not an ops nicety.
  - **Rotation stays the sharp edge**: collision-freedom holds **per key**, so a new key can map a
    fresh sequence value onto a number already minted under the old one (~10⁻³ at 10⁶ customers in
    a 10⁹ space). Treat the key as long-lived; the no-in-transaction-retry rule below is what makes
    the rare collision safe rather than corrupting.
- **🔴 The generator cannot live in `..infrastructure.id..`.** It needs
  `nextval('customer.customer_number_seq')`, and the `jooq_only_used_in_db` ArchUnit rule permits
  `org.jooq..` **only** in `..infrastructure.db..` and `..jooq..`. The `..infrastructure.id..`
  carve-out documented in `CLAUDE.md` is from **I/O sibling isolation only** — it does not extend to
  jOOQ confinement. This never bit the account generators because they use `com.github.f4b6a3`, which
  is pure Java. Two acceptable shapes, pick one explicitly:
  **(a)** put the whole generator in `..infrastructure.db..` — it *is* a DB adapter; or
  **(b)** keep the FPE + Luhn maths in `..infrastructure.id..` behind a `CustomerNumberSequence` port
  implemented in `..infrastructure.db..`.

  **✅ [SETTLED 2026-08-10] Neither — take shape (c).** The stated principle is *business-rule
  algorithms do not live in infrastructure, and identity is a business rule*, which rules out (a)
  outright and makes (b) only half an answer: (b) still parks the mapping in `infrastructure.id`.
  So the split is by **what kind of thing each part is**, not by what it needs:

  | Part | Lives in | Why |
  |---|---|---|
  | `CustomerNumber` format + Luhn validation | `..domain..` — **already there** | what a customer number *is* |
  | The assembly rule: `zeroPad(body,9) + luhnCheckDigit(body)`, and the cycle walk keeping `body ∈ [1,10⁹)` | `..domain..` (new pure factory) | the rule that makes a number *well-formed*; pure JDK, no new dependency |
  | `long next()` — the sequence | `CustomerNumberSequence` port, implemented `..infrastructure.db..` | `nextval` is genuinely I/O |
  | `long obfuscate(long)` — the bijection | `CustomerNumberObfuscator` port, implemented **`..infrastructure.id..`** | **see the honest caveat below** |

  - **🔴 Name the port after the requirement, not the mechanism.** An earlier draft called it
    `long encrypt(long)`, which puts **FF1's vocabulary in the domain** — the same move the stated
    principle objects to, just in the other direction. The port is
    `CustomerNumberObfuscator.obfuscate(long sequenceValue)`, contract: *injective on `[1, 10⁹)`;
    not invertible without the key*. FF1 then becomes an implementation detail of a domain-stated
    requirement, and most of the taxonomy argument below dissolves.
  - **`..infrastructure.id..`, not bare `..infrastructure..`.** Unqualified is exactly the ambiguity
    that produced the (a)/(b) confusion in the first place. `id` is the right home: it is exempt
    from I/O sibling isolation and needs no jOOQ.
  - **🔴 Wiring: the domain factory cannot be `@Component`.** `domain_must_be_framework_free` bans
    `org.springframework..`, so it needs an explicit `@Bean` in a config class — name that location
    when implementing (`customer.infrastructure.db` already hosts `CustomerFlywayConfig`, but a
    neutral `customer.config` may be cleaner). It implements the **existing** domain port
    `CustomerNumberGenerator`. This BC has already been bitten once by an ArchUnit rule found late;
    do not repeat it.

  - **Architecturally legal, verified:** `domain_must_be_framework_free` is a **denylist**
    (`org.springframework..`, `org.jooq..`, `com.fasterxml.jackson..`, `io.micrometer..`, …).
    `org.bouncycastle..` is not on it, so nothing *stops* crypto in the domain.
  - **🔴 But keep the cipher behind a port anyway, and here is the honest reason — it is a
    disagreement worth recording, not a rubber stamp.** Luhn and zero-padding are business rules by
    any reading. **FF1 is not**: it is chosen for *unguessability*, a security property, and it
    drags in a keyed secret, a crypto provider with its own approval path, and rotation semantics.
    "The number must not be guessable from another" is the domain rule; "FF1 with key *k*" is one
    implementation of it. Putting the key-handling in the domain would also make the aggregate's
    test setup depend on key material. **So the principle is honoured where it bites — the
    *algorithm that defines a valid number* is in the domain and is mutation-tested — while the
    keyed primitive stays swappable.** If you read the principle as covering FF1 too, say so and
    I will move it; it is legal, and the disagreement is about taxonomy, not architecture.
  - **This also improves the mutation story**, which was the tie-breaker under item 4: the branchy
    part (cycle walk, padding, check digit) lands in `..domain..`, which has *never* been excluded
    from the gate — strictly better than shape (b)'s `..infrastructure.id..`, which is only covered
    because item 4 deliberately declined the package-wide exclusion.
  - **`CustomerNumberGenerator`'s javadoc is now wrong** and must be updated with this: it says
    "the implementation is infrastructure, since minting needs a database sequence". Under (c) the
    *sequence* is infrastructure; the *minting rule* is not.
- **Collision-freedom holds *per key*, not globally.** Rotation preserves already-minted numbers
  (they are persisted, never recomputed) but a new key can map a new sequence value onto a number
  already taken under the old key.
- **No in-transaction retry.** The earlier "retry 3× on `23505`" was wrong twice over: FF1 is
  deterministic, so retrying the same `seq` reproduces the same collision; and a constraint violation
  **aborts the whole Postgres transaction** unless each attempt sits in a `SAVEPOINT` — the same fact
  ALT-8 relies on. Instead: **mint outside the registration transaction** (sequences are
  non-transactional; burning values on rollback is free), and on the ~10⁻³-after-rotation collision
  (at 10⁶ customers in a 10⁹ space — **same basis as the banner above**, which an earlier draft
  stated as 10⁻⁴ against an unstated customer count; it is the one number the FPE-vs-random decision
  might turn on, so keep both quotes on the same basis),
  let the registration fail and the client retry with a fresh `nextval`. Treat the key as long-lived
  and the problem does not arise.
- **Tests:** the mapping restricted to `[1, 10⁹)` is **injective over a sample** (a "bijection over a
  sample" test will not catch the domain-boundary defect); the Luhn digit verifies; `0000000000` is
  never emitted; a leading-zero number round-trips through the repository.

### Settled 2026-08-04 — multiple nationalities
- **`nationality` → `nationalities: Nationalities`.** Dual citizenship is common, and a U.S. citizen
  is a U.S. person for FATCA **regardless of residence**.
  **Scope honestly:** this *removes a structural blocker*, it does not "solve FATCA". A U.S. Person
  is a citizen **or** resident individual, and Model IGA Annex I indicia also include U.S. place of
  birth, address, phone, and standing payment instructions (B-10). Do not let this document be cited
  as "FATCA handled in increment 3".
- **`CorporateDetails.countryOfIncorporation` stays singular.**
- **Min 1 nationality.** *Known exclusion, not an oversight:* statelessness is legally recognised
  (UN 1954 Convention) and refugees are a real onboarding segment; supporting them needs a document
  pathway (travel document instead of passport), not an empty set. Record it — compliance will ask.
- **Unordered in the model, ordered on the wire.** `Set.copyOf` randomises iteration order per JVM
  run, so `toString()`, any JSON array, and any **idempotency fingerprint** differ per run — the
  fingerprint one silently breaks dedup after a rolling restart. **Sort at every serialisation
  boundary**: the domain→DTO mapper, the ALT-9 fingerprint, `admission_decision`, any export.

### Settled 2026-08-04 — `reconstitute`, exactly one
The count follows the number of **sources you rebuild from**. `Account` is event-sourced → two
sources → `rehydrate` + `rehydrateFromSnapshot`. `Customer` is state-stored → **one source** (the
row) → **one** `reconstitute`. Being non-event-sourced is the argument *for* it.

| | `register(...)` | `reconstitute(...)` |
|---|---|---|
| Means | a customer comes into existence | one that already exists is loaded |
| Status | forces `ONBOARDING` | accepts whatever the row says |
| Called by | the use case | the repository adapter, **only** |
| Validates | creation invariants | row consistency: `ACTIVE ⟺ activatedAt present` |

- **Not a dumb all-args constructor.** `ACTIVE` + null `activatedAt` is corruption → third tier, 500,
  alert, **and must not echo row contents**. Mirror with a DB `CHECK`.
- **Parameter object** (`AccountSnapshot` precedent) named **`CustomerState`** — ES vocabulary must
  not leak into a non-ES BC, and it prevents silent swaps between same-typed positional args.
- **ArchUnit enforces visibility**: only `..infrastructure.db..` may call it. The rule will **not**
  constrain tests (`ImportOption.DoNotIncludeTests`) — intended; say so in the rule comment.

### [corrected + ALT-8] The transition insert **is** the concurrency control
The original plan called a lost update "harmless — both converge on ACTIVE". **False.** Under READ
COMMITTED two concurrent `activate` calls both read `ONBOARDING`, both take the ONBOARDING arm, and
both return `true`. Only the *status* converges.

**Harms, in v1 order of relevance:**
1. **`activatedAt` becomes last-write-wins** — "first activation wins" is not preserved. In KYC it
   anchors periodic-review anniversaries; a jittering value is an audit defect.
2. **Both callers are told they performed the activation.**
3. *(Seam B, latent)* duplicate welcome emails and integration events. v1 emits nothing external, so
   this is not a current harm — listed third deliberately, because 1 and 2 alone justify the fix.

**ALT-1's table supersedes the conditional `UPDATE`:** the audit row and the concurrency guard become
the same operation. **One statement**, so *that path* cannot change status without leaving a record:

> **🔴 [corrected 2026-08-08 against what `V2` actually ships] Scope this correctly — it is a
> property of the statement, NOT of the schema.** This paragraph used to claim the guarantee was
> "structural rather than conventional". It is not. Verified and recorded in `V2`'s header: a direct
> `UPDATE customer.customer SET status = …` commits happily **with no transition row**, and can walk
> the status *backwards*. Audit completeness therefore rests on every status change going through
> the CTE — a **convention**, enforced by code review, not by the database.
> **Accepted residual, deliberately unbuilt:** closing it needs a `BEFORE UPDATE` trigger on
> `customer.customer` that permits a status change only when a matching transition row exists in the
> same transaction. Do not let this doc be cited as "the audit trail cannot have a hole".
>
> **🔴 There is a second, wider half of the same gap — identity-bearing columns.** Only `status` and
> `activated_at` are *meant* to change after registration, but `V6` records that `customer_number`,
> `kind`, `email`, `date_of_birth` and the name columns are all freely updatable with no transition
> row and no re-verification — and *"in most regimes a change of name or date of birth is a KYC
> re-verification event, not a field edit."* The prescribed guard is **column-scoped**, **not** a
> blanket `BEFORE UPDATE`, which is why it cannot simply be folded into the status guard above.
> *(`V6` prescribes the behaviour, not the mechanism. `BEFORE UPDATE OF col…` is the natural
> spelling, but mind the gotcha: it fires when a column **appears in the SET list**, not when its
> value changes — a full-row jOOQ update would trip it on unchanged values. The trigger body still
> needs `IS DISTINCT FROM` per column.)* It is owed by **the first endpoint that edits a customer** —
> in this plan, increment 5 item 4's deliberately-deferred `PUT /customers/{id}/email`. Partial
> coverage exists and is worth knowing so it is not over-trusted: `customer_admission`'s composite
> FK to `(id, kind)` pins `kind` — for **admitted** customers only, and for nobody still onboarding.

```sql
WITH ins AS (
  INSERT INTO customer.customer_status_transition
         (customer_id, sequence_no, from_status, to_status, occurred_at, actor)
  VALUES (?, ?, ?, ?, ?, ?)                        -- from_status / to_status are BIND PARAMETERS:
  ON CONFLICT (customer_id, sequence_no) DO NOTHING  -- this half generalises to every transition
  RETURNING 1
)
UPDATE customer.customer SET status = 'ACTIVE', activated_at = ?   -- this half does NOT (see below)
 WHERE id = ? AND EXISTS (SELECT 1 FROM ins);
```
The UPDATE's rowcount is the winner signal. PostgreSQL guarantees data-modifying CTEs execute exactly
once to completion, and that `RETURNING` is the only channel through which the main query observes
them — which is exactly what `EXISTS (SELECT 1 FROM ins)` uses.

**🔴 `sequence_no` is supplied by the caller, never computed in the adapter.** This is the part that
looks optional and is not. If the adapter derives it with `COALESCE(MAX(sequence_no),0)+1`, each
statement takes a fresh READ COMMITTED snapshot and the lost update returns:

```
T1: load customer                → ONBOARDING, no transitions
T2: insert seq=1, UPDATE status=ACTIVE activated_at=t2, COMMIT
T1: INSERT … SELECT MAX+1        → sees committed seq=1 → computes 2 → succeeds
T1: winner path → UPDATE activated_at=t1        ← harm #1, back again
```
…plus a **fabricated** `ONBOARDING→ACTIVE` row at seq 2 whose `from_status` was hardcoded by the
caller and never checked. **[added 2026-08-08] `V2` reaches one end of this with a single-row
`CHECK`: `sequence_no > 1 OR from_status = 'ONBOARDING'`.** The chain as a whole is not expressible
as a `CHECK`, but its *base case* is — a customer's first transition can only leave ONBOARDING,
because that is the only status `register()` produces. So the seq-2 fabrication above still gets
through; the seq-1 one does not. In a KYC audit trail that is worse than the original bug. Hardcoding `1`
"because activation is always first" is equally wrong — it silently voids the "generalises to every
future transition" claim, because B-1's transitions cannot hardcode anything.

```java
int recordTransitionAndApply(CustomerId id, int expectedSequenceNo,
                             CustomerStatus from, CustomerStatus to,
                             Instant occurredAt, String actor);   // inserts expectedSequenceNo + 1
```
> **🔴 [superseded 2026-08-10 by what item 5 shipped] The return type, the status pair and the
> identity/sequence arguments all moved.** The port is now
> `TransitionOutcome recordTransitionAndApply(LoadedCustomer, CustomerStatus, Instant, Actor)`.
> Every argument this section makes about *snapshots* and the caller-supplied sequence survives
> untouched — it is now enforced by the signature rather than asked of the caller, since id,
> sequence and source status are all taken from the one `LoadedCustomer`. Reasons in increment 4
> item 5 — in short, `== 1` vs `> 0`, nothing for `CommandMetricAspect.classify` to read, a
> `from`/`to` swap that the schema only catches while activation is the *first* transition, and
> three parameters that could disagree while still writing a plausible audit row. **The snippets
> below still say `int` and `findState`; read them for the reasoning, not for the signatures.**
**🔴 "Same snapshot" means "same statement", not "same transaction".** Under READ COMMITTED every
statement takes a fresh snapshot, so loading the aggregate and the sequence separately reintroduces
the identical defect through the *reader*:

```
T1: SELECT customer row            → ONBOARDING, activated_at NULL     (snapshot S1)
T2: full transition, COMMIT        → seq 1, ACTIVE, activated_at = t2
T1: SELECT COALESCE(MAX(seq),0)    → 1                                 (snapshot S2)
T1: activate() → true; INSERT seq = 1+1 = 2 → no conflict → UPDATE activated_at = t1
```

So the load is **one repository method backed by one SQL statement**, with the sequence as a scalar
subquery in the same `SELECT`:

```java
record LoadedCustomer(Customer customer, int sequenceNo) {}
Optional<LoadedCustomer> load(CustomerId id);
```
The `reconstitute` ArchUnit rule helps — the use case cannot assemble `Customer` + `sequenceNo`
itself — but it does **not** make this safe on its own: nothing stops the adapter exposing `load(id)`
and `currentSequenceNo(id)` as two methods, which reintroduces the two-snapshot bug. The real
enforcement is the port shape plus increment 4 item 10's stale-`expectedSequenceNo` test. Don't skip
the test on the grounds that "the architecture forces it".

That is what makes "`sequence_no` *is* the version" true rather than aspirational — optimistic
concurrency with the version living in the table that has to exist anyway.
**Scope the claim honestly:** `sequence_no` versions **status transitions only**. It gives no
concurrency control over any other mutation — the deferred `PUT /customers/{id}/email` would have
none at all. The moment a second mutating endpoint lands, this needs revisiting.

Why this over the alternatives:

| | |
|---|---|
| vs. **conditional `UPDATE … WHERE status='ONBOARDING'`** | Works, but leaves two separable operations — a future path can change status without writing the audit row. Folding into one statement makes forking impossible **on that path**; it does not stop a *different* path from updating `customer.status` directly (see the correction above). The **INSERT half** generalises to every transition unchanged; the `SET` clause does not (see below). |
| vs. **letting the PK violation throw** | Postgres aborts the **whole transaction** on a constraint violation unless each attempt is wrapped in a `SAVEPOINT`. `ON CONFLICT DO NOTHING` stays rowcount-driven and exception-free — the same reason `ProcessedTransactionRepository` uses it. |
| vs. **`SELECT … FOR UPDATE`** | Simpler to reason about, but holds a row lock for the transaction and still needs a separate audit write. |
| vs. **optimistic `WHERE version = ?`** | `sequence_no` *is* that version. |

- **The `version` column on `customer` is deleted** — a second, never-written counter is cruft.
  **`sequenceNo` lives on `LoadedCustomer`, not on `CustomerState`.** `CustomerState` is
  `reconstitute`'s input (and the rowcount-0 re-read's carrier); it has no use for a sequence it
  would have to accept and ignore, and `Customer` has no such field.
- **READ COMMITTED is load-bearing and the project pins no isolation level.** **[corrected
  2026-08-13, measured on PG 17.10 with two concurrent sessions running the CTE the adapter emits —
  the earlier claim was right about the outcome and wrong about the mechanism at SERIALIZABLE.]**

  | Isolation | Loser's CTE | Re-read | Net |
  |---|---|---|---|
  | READ COMMITTED | blocks on the speculative-insertion lock until the winner commits, then rowcount 0 | sees the winner's `ACTIVE` row | **`SUPERSEDED`, as designed** |
  | REPEATABLE READ | raises `40001` | never runs | fails loudly |
  | SERIALIZABLE | **succeeds with rowcount 0** — so the branch *does* run, contra the old wording | raises `40001` | fails loudly, one statement later |

  The reassuring half: a misconfigured isolation level fails **loudly** rather than fabricating a
  `CustomerRowCorruptException`. Postgres documents none of this above READ COMMITTED, so re-measure
  rather than re-reason if an isolation level is ever pinned. **The realistic vector is
  configuration, not code** — Hikari's `transaction-isolation`, or a per-role
  `default_transaction_isolation` — which is why no annotation or javadoc defends against it and
  this table is the actual control.
  **[added 2026-08-08] There are now TWO independent dependencies on READ COMMITTED, and `V1` says
  to revisit them together.** The second is the nationality-cardinality trigger, which takes
  `SELECT … FOR UPDATE` on the parent row and counts — a lock that closes the race *at READ
  COMMITTED only*.
- **🔴 [added 2026-08-08] The deferred triggers reach the ACTIVATION path, not just the recorder.**
  `trg_customer_nationality_cardinality` is attached to `customer.customer` for
  `AFTER INSERT OR UPDATE`, deferred — so activation's
  `UPDATE customer.customer SET status='ACTIVE', activated_at=?` **queues it**, and at `COMMIT` it
  locks the customer row and counts nationalities. Two consequences for **increment 4 item 15**
  (`ActivateCustomer`, moved there from increment 5):
  (a) a failure can arrive at `COMMIT`, where a `try/catch` around `recordTransitionAndApply` cannot
  see it (the message is safe — UUID + count, no `DETAIL`); (b) activation now **contends for the
  parent row lock** with any concurrent nationality write, pinned by
  `CustomerSchemaConstraintsTest.a_nationality_change_contends_for_the_customer_row`.
- **⚠️ Do NOT add `AND status = ?from` to the UPDATE.** It is the first "hardening" any reviewer will
  propose and it is wrong here: PostgreSQL executes a data-modifying CTE *exactly once and always to
  completion*, independently of whether the primary query reads its output. So the extra predicate
  would commit an audit row while changing no state, and drive the caller into the
  `IllegalStateException("unreachable")` branch. The `ON CONFLICT` is the only guard needed.
- **The `SET` clause is activation-specific.** `SET status='ACTIVE', activated_at=?` does not
  generalise; B-1's implementer must parameterise `to` and decide `activated_at` handling per
  transition rather than reusing this method blind.
- *Free correctness property worth knowing:* `ON CONFLICT DO NOTHING` **blocks** on an uncommitted
  conflicting row, so by the time the caller sees rowcount 0 the winner has already committed. That
  is what makes the fresh-snapshot re-read below reliable rather than racy.

**Composition — owned by the use case, not the adapter:**

> **🔴 SUPERSEDED by increment 4 item 15 (shipped 2026-08-13) — read the snippet for the reasoning,
> never for the code.** `CustomerRowMissingException` was renamed `CustomerNotFoundException`, both
> reads dropped their `Optional` and now throw from the adapter, `findState` is
> `findStateAfterSupersededTransition`, `recordTransitionAndApply` takes the `LoadedCustomer`, and
> the two exception choices below are wrong — see the mapping table at item 15. **The bullet three
> lines under the snippet is right where the snippet is wrong; that is not a contradiction to
> resolve locally.** Do not copy this into the ADR as written.
```java
LoadedCustomer loaded = repository.load(id).orElseThrow(CustomerRowMissingException::new);
Customer customer = loaded.customer();             // aggregate AND sequenceNo from ONE statement

boolean attempt = customer.activate(now);          // in-memory guard: whether to try at all
if (!attempt) {                                    // already ACTIVE in this snapshot; the loaded
  return ActivationResult.alreadyActive(           // row is authoritative, no re-read needed
      customer.getActivatedAt().orElseThrow(() -> new CustomerRowCorruptException(...)));
}
if (repository.recordTransitionAndApply(
        id, loaded.sequenceNo(), ONBOARDING, ACTIVE, now, actor) == 1) {
  return ActivationResult.activated(now);
}
// Lost the race — the ONLY path that re-reads. Fresh snapshot in the same tx under READ COMMITTED.
CustomerState fresh = repository.findState(id).orElseThrow(CustomerRowMissingException::new);
return switch (fresh.status()) {                   // exhaustive: enum growth breaks the build HERE
  case ACTIVE     -> ActivationResult.alreadyActive(
                         // findState bypasses reconstitute, so re-assert the third-tier invariant
                         requireNonCorrupt(fresh.activatedAt()));  // nullable Instant, not Optional
  case ONBOARDING -> throw new IllegalStateException("unreachable: lost update without transition");
};
```
- **The rowcount is the authority, the boolean is not.** In the race the boolean is `true` on both
  threads; deriving the response from it makes the whole mechanism inert.
- **The loser's in-memory aggregate is stale** — already mutated to ACTIVE with the loser's
  `occurredAt`, which was never persisted. The re-read is what makes the response truthful.
- **Both corruption paths use `CustomerRowCorruptException`** — same corruption must not produce two
  operational outcomes (a bare `orElseThrow()` gives `NoSuchElementException` → 500 with no alert).
- **It restores a tripwire the SQL loses.** `activate()`'s switch expression breaks the build when the
  enum grows; an `ON CONFLICT` clause changes meaning silently. The exhaustive switch on the re-read
  means B-1's SUSPENDED fails compilation at exactly the place that needs a decision.
- **Activation *is* `@Transactional`; the asymmetry with ALT-9b is deliberate.** ALT-9b puts a 🔴 on
  the *registration* orchestrator carrying no `@Transactional` — do not generalise that prohibition
  here. Registration needs the ban because its step 3 must commit even when step 5 throws. Activation
  has no such requirement: the CTE is a single statement, and the rowcount-0 re-read wants a fresh
  READ COMMITTED snapshot *within* the transaction, which is exactly what it gets. One boundary, one
  statement, one optional re-read.

### [ALT-9] `Idempotency-Key` + `processed_registrations`, mirroring the Account BC
Natural dedup on the email constraint was cheaper but semantically wrong: a retry with a *changed*
payload gets a 409 indistinguishable from "someone else took this email".

```sql
customer.processed_registrations (
  idempotency_key TEXT        NOT NULL PRIMARY KEY,
  fingerprint     CHAR(64)    NOT NULL,        -- SHA-256 hex, as account.processed_transactions
  customer_id     UUID        NOT NULL,        -- so a replay returns the SAME body
  occurred_at     TIMESTAMPTZ NOT NULL
)
```

- **Single round trip via CTE**, exactly as `ProcessedTransactionRepository`: `INSERT … ON CONFLICT
  DO NOTHING RETURNING` ∪ `SELECT … WHERE NOT EXISTS(ins)`. Deliberately **not** `ON CONFLICT DO
  UPDATE` — no-op writes generate WAL, dead tuples, and fire triggers.
- **Keep the account's fallback `SELECT`.** When two requests race on the same key the CTE returns
  nothing (the `INSERT` conflicts and the `SELECT` cannot see the other transaction's uncommitted row
  in the same snapshot); a separate statement takes a fresh snapshot. An already-solved subtle bug —
  copy it, don't rediscover it.
- **The fallback `SELECT` is mandatory, not optional — and with it, null is unreachable.** Verified
  against Postgres 18 (the version pinned in both `docker-compose.local.yml` and
  `AbstractContainerTest`; an earlier revision said 17, which was never a version this repo ran):
  `ON CONFLICT DO NOTHING` **blocks** on the conflicting transaction until it
  commits or aborts. So by the time the loser reaches the fallback, the winner has committed (loser
  sees a non-null `customer_id` → normal replay) or aborted (loser's own `INSERT` succeeded → normal
  create). There is no in-flight window to observe.
  **Therefore: a double-clicked submit returns the replayed 201, not a 409.** Do not treat an empty
  CTE result as "in flight" and skip the fallback — that inverts the mechanism's purpose and 409s
  every double-click.
- **Still add a third outcome, `IN_FLIGHT` → 409, as a defensive branch.** It should be unreachable
  today; it exists so that a future change — a cleanup job deleting rows, or a configured isolation
  level above READ COMMITTED — surfaces as a 409 rather than an NPE. Label it that way in the code,
  or someone will "optimise" the fallback away on the grounds that the branch exists.
- **🔴 `customer_id` carries NO foreign key, and that pins ALT-9b step 5's internal ordering.**
  Recorded in `V5` and worth having here, because it looks like an omission to tidy up: within the
  step-5 transaction the **idempotency row is inserted FIRST** — its insert is what short-circuits a
  replay before any other work happens — so the customer row does not exist yet and an FK would
  reject *every* registration. A deferrable FK would work and buys nothing: this is prunable data
  whose referent is allowed to be gone (ADR-009 D6). Do not reorder the inserts, and do not add
  the FK.
- **Fingerprint mismatch → conflict exception**, mirroring `TransactionIdConflictException`.
- **🔴 The fingerprint covers client-supplied payload ONLY** — email, details, **sorted**
  nationalities, residence. **Never the minted `CustomerId`, never `registeredAt`.** Both are
  generated server-side per attempt (random UUID; `Clock`-derived instant), so a fingerprint over the
  domain command changes on every retry and the conflict rule turns **every legitimate retry into a
  409**. The mechanism would fail closed on the exact case it exists to serve.
- **Ordering:** mint the `CustomerId` before the idempotency check (it comes from a generator, not the
  DB; a discarded UUID costs nothing) so the row can store it. On replay the stored `customer_id` is
  used to **re-read the customer row** and rebuild the same 201 body — do *not* denormalise the body
  into `processed_registrations`.
- **The key is NOT burned by a rejected admission** — a rejected applicant can correct their details
  and retry with the same key. This works **because `processed_registrations` is written at ALT-9b
  step 5, after admission**: a corrected retry finds no stored fingerprint and so cannot trip the
  mismatch rule. That ordering is load-bearing, not incidental — do not "tidy" the idempotency insert
  earlier in the flow.

### [ALT-9b] 🔴 A rejected registration must still leave an audit record
ALT-5 says the `Refused` specifics (every match: restriction, connecting factor, triggering country)
go **only** to
`admission_decision` and the audit log. If evaluation,
`Customer.register`, persistence and the decision write all sit in **one** `@Transactional` unit, a
rejection throws → Spring rolls back on the RuntimeException → **the `admission_decision` row rolls
back with it.** The audit record the entire compliance design depends on is never committed. In a
regulated onboarding flow that is backwards: you must be able to evidence years later why you refused
someone, and in several regimes the refusal itself is reportable.

**Transaction plan — three boundaries, in this order:**

| Step | Boundary | Notes |
|---|---|---|
| 1. Replay pre-check on `processed_registrations` | read, no tx needed | fast path; avoids wasting an admission evaluation on a retry. Not the authority — **step 5's** CTE is. |
| 2. Mint `CustomerId` + `CustomerNumber` | **outside any transaction** | sequences are non-transactional (ALT-7); no savepoint problem |
| 3. Evaluate admission + write `admission_decision` | **its own transaction**, commits unconditionally | **Beware Spring self-invocation** — a `REQUIRES_NEW` method called from within the same bean is not proxied and silently joins the caller's transaction, reintroducing the exact bug. Use a separate collaborator bean or an explicit `TransactionTemplate`. |
| 4. Reject → throw 422 (generic body) | — | the decision is already durable |
| 5. Register: CTE idempotency insert → short-circuit on replay/`IN_FLIGHT`; else insert customer + nationalities | **its own transaction** | |

- **🔴 The orchestrating method carries NO `@Transactional`.** Steps 3 and 5 are sequential, each
  opening its own boundary via a collaborator bean or `TransactionTemplate`. If the orchestrator is
  annotated, step 3 joins it and the rollback bug returns silently — which is exactly the failure
  this section exists to prevent. There is no outer transaction, so there is no double-connection
  concern either.
- **🔴 `admission_decision.customer_id` must be NULL on every path, with no FK.** At step 3 the
  customer row does not exist yet in *any* scenario. Writing the minted UUID there produces a
  fabricated reference whenever step 5 short-circuits (replay) or rolls back (email conflict,
  post-rotation number collision) — and with no FK nothing catches it, so an investigator joining
  `admission_decision → customer` gets a phantom rather than a NULL. **Correlate on
  `idempotency_key`**, which is stable across both outcomes.
- Carry enough applicant identification (the idempotency key and the evaluated inputs) to make the
  record meaningful. That identification is PII → covered by B-2.
- *Accepted consequence:* a crash between steps 3 and 5 leaves a decision with no customer row. That
  is accurate, not corrupt — we did decide, then failed to persist. A concurrent duplicate produces
  one extra decision row; benign.
- **⚠️ Three deliberate decisions compose into unbounded write amplification. Rate limiting is a
  prerequisite for shipping `POST /customers` publicly.** Step 3 commits `admission_decision`
  **unconditionally**; `idempotency_key` carries **no unique constraint** (each attempt is its own
  audit record); and a refused key is **deliberately not burned** (so an applicant can correct and
  retry). Each is individually justified above. Together, N replays of one refused payload write N
  rows to the table under **5-year retention** — the one table that cannot be pruned — and there is
  no authz or throttling anywhere in the project today (increment 5 item 8). This is **not** a
  tipping-off leak: the 422 is generic, so the caller learns nothing. It is a storage and cost
  exposure, and it is worth recording here so it is not discovered in production.

---

## Increment 4 — persistence

0. ✅ **DONE 2026-08-06 — `docs/adr/009-pii-retention-and-erasure.md`.** Not "a paragraph": the repo
   already tracked this as **WP-116**, whose *done when* reads "**Do not merge the customer
   infrastructure layer before this decision is recorded**" and demands a position for Kafka,
   outbox, projections, snapshots and backups. B-2 and WP-116 are one decision, so they got one ADR.
   What it changed that the rest of this plan must absorb:
   - **🔴 ALT-1's `REVOKE` is inert, and `ON DELETE CASCADE` would have voided it anyway.** Three
     things verified on PG18, each defeating append-only silently: (a) referential actions run as
     the **table owner**, so `ON DELETE CASCADE` deletes rows the caller is forbidden to delete;
     (b) **the app, Flyway and Debezium all connect as `POSTGRES_USER=user`, the bootstrap
     superuser that owns every table** — no non-owning role exists on the write path (the only
     separated role is the read-only `monitoring` role in
     `docker/postgres/bootstrap/02-monitoring-role.sql`, which is also where the future purge role
     belongs), and a superuser bypasses ACLs entirely, so the `REVOKE` is documentation;
     (c) **`TRUNCATE … CASCADE` defeats both `RESTRICT` and row-level triggers**; (d) **`SET
     session_replication_role = replica` defeats triggers AND referential actions in one
     statement**, leaving orphan rows. So item 2 gains: **append-only triggers as the
     enforcement** — `BEFORE UPDATE`/`BEFORE DELETE` row-level **plus statement-level `BEFORE
     TRUNCATE`**, all **`ENABLE ALWAYS`**, reusing the `V3__event_store_append_only.sql` pattern —
     on `customer_status_transition`, `admission_decision`, `admission_decision_match` **and
     `customer_admission`**; **`ON DELETE RESTRICT`** on the audit FKs and **`ON DELETE CASCADE`**
     on `customer_nationality`'s **composite** FK; and the `REVOKE`s kept
     as declared intent. Maintenance uses `ALTER TABLE … DISABLE TRIGGER` in-transaction, **never**
     `session_replication_role`. *Bonus finding: `account.event_store`'s V3 triggers are bypassable
     on **two** paths (`TRUNCATE` and replica mode) — fix forward with the statement-level trigger
     **and** `ENABLE ALWAYS`.*
     - **🔴 [corrected 2026-08-08 by what `V6` shipped] `ON DELETE RESTRICT` is NOT defence in depth
       for the population that matters, and `V6` guards three tables this list never mentioned.**
       This bullet called RESTRICT "defence in depth". Verified in `V6`: an **ONBOARDING customer
       has zero transition rows, so RESTRICT never engages for them** —
       `DELETE FROM customer.customer WHERE status = 'ONBOARDING'` succeeded, cascaded the
       nationalities away, and left no trace. Per ADR-009 D3 the abandoned-onboarding population is
       likely the **largest** personal-data category in the schema. Hence `V6` also guards:
       `customer.customer` (`DELETE` + `TRUNCATE` — **not `UPDATE`**, deliberately: activation must
       still write `status`/`activated_at`), `customer.customer_nationality` (`TRUNCATE` only, since
       the composite-FK cascade is wanted), and `customer.admission_policy` (`DELETE` + `TRUNCATE`).
       **Two things the purge path (item 0 / B-1) must now absorb:** RESTRICT is not a delete guard
       for onboarding customers, and the purge must `ALTER TABLE … DISABLE TRIGGER` on
       `customer.customer` **itself**, not only on the audit tables.
     - **🔴 The `DISABLE TRIGGER` idiom has two obligations that "in-transaction" does not convey**,
       both learned the hard way in `SchemaProbe`, which says outright that *"whatever implements
       the retention purge inherits the same obligation"*. Neither `V6` nor ADR-009 D5 states them,
       so this doc is their only home before the purge is built:
       1. **`DISABLE TRIGGER` is DDL, and DDL is transactional — so it rolls back with a rollback
          *and commits with a commit*.** The purge must re-enable **inside the same transaction**.
          In `SchemaProbe` the omission left the guard off for the rest of the JVM and silently
          turned a later assertion from "the trigger refused this" into "a foreign key refused
          this" — weakening every subsequent check instead of failing.
       2. **Re-enable with `ENABLE ALWAYS TRIGGER`, never plain `ENABLE TRIGGER`.** The plain form
          sets `tgenabled = 'O'`, silently downgrading the guard to the exact
          replica-mode-silenceable state the whole design exists to avoid. In production nothing
          re-checks: the catalog assertions live in `V6`'s apply-time `DO` block and in a test
          container.
   - **🔴 A new table: the customer→decision link.** `admission_decision` has no `customer_id` (ALT-9b,
     correctly — no customer exists at step 3). But an **admitted** decision is CDD evidence retained
     to *relationship end + 5y*, and its only path to the customer ran through
     `processed_registrations`, which is prunable. Write a `(customer_id, decision_id)` link **inside
     ALT-9b step 5's transaction**, where the customer row does exist so no phantom reference
     arises. Refusals never acquire a link, so the two outcomes stay structurally distinguishable.
   - **🔴 B-2's "genuine conflict" framing is withdrawn.** GDPR Art. 17(3) disapplies erasure *"to
     the extent that"* processing is necessary — it **suspends** the right, then AML law
     (4AMLD Art. 40(1) **2nd** subpara, not "final") **mandates** the deletion it had blocked. A
     sequence, not a conflict. Do not build a reconciliation mechanism for it.
   - **🔴 Retention cannot be a constant.** 4AMLD is a *directive* and Art. 5 permits stricter
     national rules, so 5 years is a floor, not an EU value; and AMLR Art. 77(3) replaces the basis
     on **10 Jul 2027**. → property **`customer.retention.cdd-years`**, never a literal.
     - **🔴 [2026-08-10] The default is `10`, not `5`** — Swiss AMLA Art. 7(3), from termination of
       the relationship or completion of the transaction. ADR-009 defaults it to `5` on an explicit
       EU/UK premise that no longer holds. See the falsification banner at the top of this file.
     - **✅ [SETTLED 2026-08-10] Treat the scalar as a first step — but justify the seam on the
       variation that exists TODAY, not on the jurisdiction one.** The stated direction is that
       retention will later vary by jurisdiction *and other criteria*, so a single global `int` is
       known-temporary. **The jurisdiction argument is the weak one** and should not be the
       justification: "maybe others later" is a *maybe*, and one property → one lookup table is a
       cheap change to make when it actually arrives. On that argument alone this would be
       speculative generality.
       **The strong argument is already written down in ADR-009 D3: five categories, three
       different anchors, and they differ NOW** — refusal (`decided_at`), relationship end
       (`CLOSED`), abandoned onboarding (no anchor at all) — with, as of 2026-08-10, **different
       legal bases too** (the refusal anchor lost its AMLR basis; an Art. 9(1)(b)-reported refusal
       is a different record class again). A scalar cannot express that, jurisdictions aside.
       So: `Period retentionFor(RetentionSubject)` keyed on a **sealed record category**, never a
       jurisdiction argument the sole implementation ignores.
     - **🔴 But do NOT create the type yet — bind it to its first consumer.** Verified: nothing in
       `src/` reads `customer.retention.cdd-years` today; only a comment in `V2` names it, and no
       purge path exists. An interface with one implementation and **zero call sites** is precisely
       what this document polices elsewhere. **Record the convention now** — *retention is resolved
       through one call keyed on record category, never a `@Value` at the point of use* — and
       **create the type with the purge job**.
   - **🔴 [CORRECTED 2026-08-10 — this was the THIRD casualty of the Switzerland answer, and the
     top-of-file banner missed it.]** This bullet used to read: *"AMLR Art. 77(3) names 'date of
     refusal' as a statutory retention trigger, so `decided_at` is the **legal** anchor for refused
     applicants."* **AMLR is Regulation (EU) 2024/1624 and binds EU obliged entities — not a Swiss
     firm.** And Swiss AMLA Art. 7(3)'s triggers are *relationship termination* and *transaction
     completion*; **a refused applicant has neither**. So under the answered jurisdiction the
     refusal anchor has **no established statutory basis**, and the "second independent
     justification" is withdrawn.
     - **The column survives, the claim does not.** `decided_at` is still required — the "refusals
       last quarter" query needs it and the retention clock has to start *somewhere*. What must not
       be extracted into the ADR is the sentence calling it the statutory anchor.
     - **Blast radius the banner does not yet cover** — sweep these in the ADR-009 amendment rather
       than one at a time: ADR-009 **D3 row 1** and its "second independent justification"; the
       purge disjunction *"first production refusal + `cdd-years`"* wherever it appears (item 0,
       B-1(d)); D3's *"the AMLR refusal trigger becomes explicit on 10 July 2027 regardless"* — not
       our date; the Positive consequence *"retention survives 10 July 2027 as a configuration
       change"*; and the `Retain indefinitely` rejection, which leans on 4AMLD's +5-year **ceiling**
       — Swiss AMLA sets a floor with **no maximum**, so the ceiling now has to come from FADP
       Art. 6(4) instead, which is a different mechanism entirely.
   - **🔴 The purge path is a prerequisite on a *disjunction*, not on B-1 alone.** The customer
     anchor ("end of business relationship") is uncomputable while `CustomerStatus` is
     `{ONBOARDING, ACTIVE}` — compliant *by construction*. But the **refusal** clock
     (`decided_at`) starts unconditionally, so the real deadline is **whichever comes first:
     `CLOSED` shipping, or first production refusal + `cdd-years`** — compliant *by calendar*, and
     calendars expire on their own. B-1 also owns the **abandoned-onboarding** anchor (registered,
     never activated → no anchor at all, likely the highest-volume PII category).
   - Also settled there: `processed_registrations` is **not** pinned to audit retention (item 2's
     open question). **Scope that correctly — it holds only for refusals.** A *refused* decision
     stands alone on its evaluated inputs and `decided_at`; an **admitted** one is CDD evidence
     retained to relationship end + 5y, which is why the link table above exists.
1. ✅ **DONE 2026-08-06 (`40f3d22`) — Flyway bean split + item 9, one commit.** `shared.config.ModuleFlyway`
   (static factory: schema in, configured `Flyway` out) + `account.infrastructure.db`
   `AccountFlywayConfig` (`Flyway` + `FlywayMigrationInitializer`); the three dead
   `spring.flyway.locations`/`default-schema`/`schemas` keys deleted;
   `AbstractContainerTest` moved to `org.girardsimon.wealthpay.testsupport`. Verified on a clean
   container (17 migrations applied to schema `account`) **and** on the real component-scanned app
   (`Current version of schema "account": 17`, started clean). Full build green.
   - **🔴 Deviation, deliberate: only the `account` bean landed.** The plan said "declare **both**".
     The customer bean lands atomically with its first migration in **item 2**, because
     `ModuleFlyway` sets **`failOnMissingLocations(true)`** — a bean pointing at a not-yet-existing
     `db/migration/customer` would fail the boot, and weakening that setting to accommodate a
     placeholder trades a real guard for a placeholder. The trap the "both" instruction guarded
     against (declaring customer's bean silently kills the auto-configured account one) is already
     dead: account's bean is now explicit.
   - **🔴 The stated failure mode was wrong, in a way that matters for item 2.** The plan expected
     "silent dead config, green tests". Verified against Boot 4.0.2: the properties go inert **only
     in the app**. Every container test is a **`@JooqTest` slice**, which does *not* component-scan,
     so an un-imported `@Configuration` leaves `FlywayAutoConfiguration` in charge — and it *is*
     reachable in the slice, via `@JooqTest → @AutoConfigureJooq →
     @AutoConfigureDataSourceInitialization`, whose `.imports` is contributed by `spring-boot-flyway`.
     So prod uses beans, tests use properties, and they diverge silently. Hence
     the per-BC `@Import`, verified to reach subclasses that declare their own `@Import`
     (`ConfigurationClassParser` recurses into superclasses).
   - **⚠️ Known, accepted, NOT closed: every other `spring.flyway.*` key is inert with no warning.**
     `baseline-on-migrate`, `out-of-order`, `validate-on-migrate`, `target`,
     `ignore-migration-patterns` and `url`/`user`/`password` all do nothing now. The javadoc says so,
     but its reader is a developer editing `application.properties` — not an operator setting
     `SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS` in a deploy manifest during an incident. Two fixes
     were costed and deferred, deliberately: (a) take `ObjectProvider<FlywayMigrationStrategy>` in
     the initializer bean to re-arm the standard `repair()`-then-`migrate()` lever — two lines, but
     it needs a code change and a deploy either way, so it does not actually serve the 02:00 case;
     (b) a boot-time guard that refuses to start when an inert `spring.flyway.*` key is set — the
     real fix, and it **must match the relaxed/normalised form**, since env vars arrive as
     `SPRING_FLYWAY_OUT_OF_ORDER` through `SystemEnvironmentPropertySource` and a canonical
     dotted-name match would miss exactly the channel that matters. Do (b) when a second BC makes the
     blast radius real, or sooner if anyone touches deploy manifests.
   - **`spring.flyway.enabled` settled: honoured, not deleted.** `@ConditionalOnBooleanProperty(...,
     matchIfMissing = true)` on each BC config. Deleting the key from `application.properties` would
     not have removed it from Spring Boot's namespace — `SPRING_FLYWAY_ENABLED=false` would still
     have produced the broken half-state (auto-config off, our beans migrating anyway,
     `DatabaseInitializationDependencyConfigurer` silently dropped).
   - **The initializer beans are mandatory, not decorative.** Nothing else calls `migrate()` once
     the nested `FlywayConfiguration` backs off. The plan's note is still right that they are *not*
     needed for ordering (`FlywayDatabaseInitializerDetector` detects plain `Flyway` beans —
     confirmed in the 4.0.2 jar, alongside `FlywayMigrationInitializerDatabaseInitializerDetector`).
   - **🔴 Fixed while here: `flyway-core` and `flyway-database-postgresql` were a major version
     apart.** `<flyway-core.version>12.0.1</flyway-core.version>` never reached core — the Boot BOM
     reads `flyway.version` — so it only bumped the *database* module, leaving core at 11.14.1 and
     postgres at 12.0.1. Flyway requires them in lockstep; mixing is the documented cause of
     `Unsupported Database` / `NoSuchMethodError`. The explicit `<version>` and the dead property are
     gone; the BOM now manages both at **11.14.1**, the pair Boot 4.0.2 is tested against. Re-verified
     by booting the real app. A deliberate move to 12.x is a separate upgrade with its own validation.
   - **Three new guards, each verified to fail when the code is broken** (not merely green — five
     deliberate mutations, five red builds): `FlywayWiringTest` (architecture package) asserts
     (a) the set of `db/migration/<x>` directories equals the set of schemas declared by `Flyway`
     beans found by classpath scan — this is what catches "item 2 added migrations and forgot
     `CustomerFlywayConfig`" — and (b) every config declaring a `Flyway` bean also declares a
     `FlywayMigrationInitializer` **`@Qualifier`-bound to that same bean**. The qualifier half is
     the copy-paste trap item 2 walks into: an initializer left pointing at `accountFlyway`
     migrates `account` twice (idempotent, silent) and never creates the `customer` schema.
     ~~`AccountFlywayConfigTest` asserts `spring.flyway.enabled=false` actually removes both
     beans.~~ **[corrected 2026-08-08] That class was deleted in `f767909`** and subsumed by a
     third, scan-driven check in `FlywayWiringTest`
     (`every_flyway_configuration_is_removed_by_the_kill_switch`), which asserts the kill switch for
     **every discovered** configuration rather than for one hand-written BC. So `FlywayWiringTest`
     carries **three** checks, not two — and the customer config inherited the guarantee for free.
     `ModuleFlywayTest` pins the location derivation and the `cleanDisabled`/`failOnMissingLocations`
     settings.
   - **Per-BC test base, not a central import list.** A first draft put `@Import(AccountFlywayConfig)`
     on the neutral `AbstractContainerTest`, which re-created the cross-BC coupling the move existed
     to remove (`testsupport` → `account`) and would have made every `customer` slice run all 17
     account migrations, with a broken customer migration failing the account suite. Corrected:
     `AbstractContainerTest` is BC-neutral (container + `spring.datasource.*`), and
     `account.infrastructure.db.repository.AbstractAccountContainerTest` carries the `@Import`.
     **Item 2 adds `CustomerFlywayConfig` + an `AbstractCustomerContainerTest`** — and
     `FlywayWiringTest` fails loudly if the config is forgotten.
2. ✅ **DONE 2026-08-08 (`e1f39da`) — six migrations, `CustomerFlywayConfig`, and three test
   classes.** `V1__init_customer_schema` · `V2__customer_status_transition` ·
   `V3__admission_policy` · `V4__admission_decision` · `V5__processed_registrations` ·
   `V6__customer_write_guards`. Verified: `PerBoundedContextMigrationTest` (each BC applies its own
   migrations into its own schema, both migrated by one application context — **plus three tests
   standing guard over the `dbz_publication` invariant**, see standing rule 1 below),
   `CustomerSchemaConstraintsTest` (every `CHECK`, FK and cardinality rule below, exercised against
   a container), `CustomerAuditAppendOnlyTest` (append-only under
   `UPDATE`/`DELETE`/`TRUNCATE`/`session_replication_role`). *(No test count quoted deliberately —
   see the `CLAUDE.md` "16 rules" entry under Doc updates owed for why counts in prose rot.)*
   **Everything below this bullet is now the *rationale of record*, not a to-do list** — it is why
   the schema looks the way it does, and it is the material the item 13 ADR extracts. Three
   deviations from what was planned:
   - **🔴 Cross-table cardinality is enforced in the DB after all — by *deferred* `CONSTRAINT
     TRIGGER`s, and increment 5 must be built knowing it.** The plan (see the four-reason bullet
     below) concluded `outcome = 'REFUSED' ⟺ ≥1 match row` is "not expressible as a `CHECK` either
     way → enforce it in the recorder and state it as an application invariant". That conclusion was
     right about `CHECK` and wrong to stop there: `V4` enforces it with a
     `DEFERRABLE INITIALLY DEFERRED` constraint trigger on **both** tables, and `V1` does the same
     for the 1–10 nationalities rule. Stronger than planned, but it moves the failure: **a deferred
     trigger fires at `COMMIT`, not at the offending statement.** Two consequences the recorder
     (increment 5) must respect:
     - **(a) The violation surfaces on the transaction boundary**, so a `try/catch` around the
       individual insert cannot see it. The application-level invariant is still owed — the DB guard
       makes the bad state unreachable, it does not make the error legible.
     - **(b) It converts "incomplete audit record" into "NO audit record".** ALT-9b step 3 exists to
       make the refusal durable *unconditionally*; with the deferred guard, a recorder bug that
       writes a `REFUSED` decision without its match rows now fails the **whole** step-3 transaction
       at `COMMIT` and destroys the evidence, where previously it would have stored it incomplete.
       For a 5-year-retention compliance table that is the trade to be aware of — it is still the
       right one (an `ADMITTED` decision carrying a sanctions match is the worst state the schema
       can hold, per the commit message), but it raises the stakes on the recorder having exactly
       one insert path.
     - **🔴 [corrected 2026-08-08] This bullet previously claimed (b) was a tipping-off leak — it is
       not, and the error mattered in the direction that would mislead.**
       `customer.assert_decision_matches_outcome` raises with only the decision **UUID** and a match
       **count**, and its `HINT`s are compile-time constants; there is no `USING DETAIL`, so
       `logServerErrorDetail` has no server `DETAIL` to propagate. The commit-time error is one of
       the *safe* ones. The real leak channel in increment 5 item 5 is the **immediate**
       `CHECK` and `NOT NULL` violations on `admission_decision_match`, which emit
       `Failing row contains (…)` carrying restriction, connecting factor and triggering country,
       (**not** FK or PK violations — their `DETAIL` names only `decision_id`/`ordinal`,
       i.e. decision identifiers rather than match fields)
       and which fire at **statement** time. Deferral *narrows* the leak surface; do not read it as
       widening it, and do not let "the trigger message is harmless" become "there is no leak here".
   - **🔴 `licensed_country` shipped EMPTY, deliberately — and that refuses every applicant.** The
     `SANCTIONED` rows are likewise unseeded. Only the US `RESTRICTED_PERSON` triple is in, because
     it is the one rule that does *not* depend on the home authorisation jurisdiction. The migration
     says so in a comment, and `CustomerSchemaConstraintsTest.licensed_country_is_deliberately_unseeded`
     pins it so no one "fixes" it with a guess. **This is the polarity working as designed** (a
     partially-seeded deny-list is safe precisely because nothing can be admitted), but it means the
     BC cannot serve traffic until the home jurisdiction is named — see item 8, whose priority moved
     up as a result.
   - **The `middle_name` case the plan asked for is covered**:
     `an_individual_without_a_middle_name_is_accepted`. Item 10's warning that "the round-trip test
     only catches this if the fixture happens to omit a middle name" is discharged here rather than
     there.

   **🔴 Two standing rules `V1`'s header establishes that exist nowhere else in this plan** (ADR-009
   D1/D8 — item 0's absorb-list above captured D5, D6, D4 and D2, and missed these). Both constrain
   work the *next* session does:
   1. **This schema must not ship in a release that does not contain
      `db/migration/account/V18__narrow_dbz_publication_to_outbox.sql`.** These tables hold personal
      data and must not exist while a logical-replication publication carries more than
      `account.outbox`. The two Flyway instances are **unordered relative to each other**, so Flyway
      *cannot* sequence this for you.
      **There IS a standing automated control — do not invent a manual checklist, and do not treat
      those tests as incidental.** `PerBoundedContextMigrationTest` builds both BCs' Flyway beans in
      one context, and three of its tests stand on this invariant:
      `logical_replication_carries_the_outbox_and_nothing_else` (asserts `pg_publication_tables`
      contains exactly `account.outbox`), `the_outbox_publication_is_not_merely_present_but_working`,
      and `the_canonical_check_rejects_a_publication_that_carries_nothing` (six mutations, including
      `ADD TABLES IN SCHEMA customer`), backed by `account.dbz_publication_is_canonical()`. V18
      asserts the invariant once *at apply time*; these are what cover a later migration touching the
      publication. **What genuinely remains human is narrower:** production drift outside CI — an
      operator `ALTER`ing the publication on a long-lived database — which the standing test cannot
      see.
   2. **No future migration under `db/migration/customer` may seed personal data.** `V3`'s four
      policy rows — one version number, three US-person rules — are the only WAL any migration here
      is permitted to produce, and that is precisely what makes rule 1's unordered window safe. A
      migration that seeds a test customer silently breaks the invariant.
   ```sql
   customer.customer
     -- id, customer_number NOT NULL UNIQUE   <- NOT NULL load-bearing: UNIQUE permits multiple NULLs
     -- email               NOT NULL UNIQUE
     -- status              NOT NULL, CHECK (status IN ('ONBOARDING','ACTIVE'))
     -- registered_at       NOT NULL, activated_at NULL
     -- kind                NOT NULL, CHECK (kind IN ('INDIVIDUAL','CORPORATE'))   <- IS the type
     -- individual cols (NULL for corporate): given_name, middle_name, family_name,
     --                                       date_of_birth, gender, country_of_residence
     -- corporate  cols (NULL for individual): registered_name, registration_number,
     --                                        country_of_incorporation
     -- CHECK ((status = 'ACTIVE') = (activated_at IS NOT NULL))
     -- CHECK (activated_at IS NULL OR activated_at >= registered_at)  <- mirrors the aggregate;
     --   a negative interval corrupts the review anchor AND the AML retention clock
     -- CHECK (discriminant: exactly one subtype's columns populated — see below)
     -- NO version column (ALT-8: sequence_no on the transition table is the version)
   customer.customer_nationality        -- customer_id, kind TEXT NOT NULL, country_code
                                        -- PK (customer_id, country_code), INDEX country_code
                                        -- individuals only — enforced, see below
   customer.customer_status_transition  -- ALT-1: PK (customer_id, sequence_no); audit log AND guard
   customer.restricted_country          -- ALT-5 DENY side: (country_code, restriction,
                                        -- connecting_factor) PK; PARTIALLY seeded: the US
                                        -- RESTRICTED_PERSON triple only. SANCTIONED rows await the
                                        -- home jurisdiction.
   customer.licensed_country            -- ALT-5 ALLOW side: (country_code, connecting_factor) PK;
                                        -- *** SHIPPED EMPTY, DELIBERATELY *** -> every applicant is
                                        -- refused until the home jurisdiction is named. See the
                                        -- deviation bullet above; do not "fix" this with a guess.
   customer.admission_policy            -- single row: version; covers BOTH tables; SEEDED HERE
   customer.admission_decision          -- NO customer_id AT ALL (see below); surrogate id PK,
                                        -- idempotency_key, policy_version, evaluated inputs,
                                        -- outcome NOT NULL CHECK IN ('ADMITTED','REFUSED'),
                                        -- subject_type NOT NULL CHECK IN ('INDIVIDUAL','CORPORATE'),
                                        -- decided_at TIMESTAMPTZ NOT NULL   (claimed time)
                                        -- recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()  (observed)
                                        -- NO denormalised primary match (see below)
   customer.admission_decision_match    -- ALL matches. PK (decision_id, ordinal); FK decision_id
                                        -- -> admission_decision(id); restriction, connecting_factor,
                                        -- triggering_country, each NOT NULL + CHECK IN (...).
                                        -- ordinal 0 = primary (0-BASED, unlike sequence_no)
                                        -- INDEX (restriction) WHERE ordinal = 0
   customer.customer_admission          -- ADR-009 D6: the retention anchor for ADMITTED decisions.
                                        -- [AS SHIPPED in V4 - richer than this sketch, see below]
                                        -- linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                        -- PK (customer_id, decision_id)  <- COMPOSITE: the ALT-9b
                                        --   concurrent-duplicate case gives one customer 2 decisions
                                        -- UNIQUE (decision_id)  <- a decision anchors to ONE customer
                                        -- + denormalised outcome + subject_type, both NOT NULL,
                                        --   CHECK (outcome = 'ADMITTED'), and COMPOSITE FKs to
                                        --   admission_decision(id, outcome, subject_type) and
                                        --   customer(id, kind).  <- this is what makes "a refusal
                                        --   never acquires a link" a CONSTRAINT rather than a
                                        --   write-path convention; the two-plain-FK version in this
                                        --   sketch let a test fixture link a REFUSED decision and
                                        --   commit. 🔴 NEVER READ the denormalised columns - join
                                        --   to admission_decision instead; they exist to be
                                        --   constrained, not queried. THE REASON MATTERS, or the
                                        --   rule gets optimised away: composite FKs are INTERNAL
                                        --   triggers, not ENABLE ALWAYS, so replica mode SKIPS
                                        --   them - a link committed that way reads
                                        --   ADMITTED/CORPORATE while the decision says REFUSED.
                                        --   Under the plain FKs these columns replaced, that state
                                        --   was at least visible to anything that joined; these
                                        --   columns read CLEAN. Strictly worse, in that one
                                        --   scenario, for anything that trusts them.
                                        -- RESIDUAL (V4): an ADMITTED CORPORATE decision links
                                        --   cleanly to ANY corporate customer - the FK proves the
                                        --   types agree, not that this is the RIGHT customer.
                                        --   Pairing correctness is on increment 5 item 1.
                                        -- append-only (ENABLE ALWAYS triggers), written in step 5
   customer.processed_registrations     -- PK idempotency_key; fingerprint CHAR(64); customer_id
   customer.customer_number_seq         -- START > 0 (ALT-7 cycle walk needs it), MAXVALUE 999999999
   ```
   - **Per-subtype nullable columns, not JSONB.** FATCA/CRS reporting queries DoB and names directly;
     JSONB would need GIN and expression indexes for the same result. The discriminant `CHECK` is
     what keeps the sealed hierarchy honest at rest.
   - **🔴 The `CHECK` must distinguish *required* from *optional* subtype columns.** "Each subtype's
     columns `IS NOT NULL`" is wrong: `PersonalName.middleName` is explicitly optional (null-normalised,
     with a two-arg `of()` factory), so a blanket rule rejects **every registration without a middle
     name**. Correct shape:
     ```
     kind='INDIVIDUAL' → given_name, family_name, date_of_birth, gender, country_of_residence
                         IS NOT NULL;  middle_name MAY be NULL;
                         registered_name, registration_number, country_of_incorporation IS NULL
     kind='CORPORATE'  → registered_name, registration_number, country_of_incorporation
                         IS NOT NULL   (all three required);
                         middle_name AND every individual column IS NULL
     ```
     Item 10's round-trip test only catches this if the fixture happens to omit a middle name — add
     that case explicitly.
   - **`customer_nationality` is individuals-only — enforce it, don't assert it** (same standard as
     ALT-1's `REVOKE`): denormalise `kind` onto the child, then `UNIQUE (id, kind)` on `customer`, a
     composite FK `(customer_id, kind) → (id, kind)`, and `CHECK (kind = 'INDIVIDUAL')` on the child.
     **🔴 The child's `kind` must be `NOT NULL` or both constraints are bypassed at once** — verified
     against Postgres 18 (an earlier revision said 17; this repo has only ever run 18): the default
     `MATCH SIMPLE` skips the FK check entirely when *any*
     referencing column is NULL, and `CHECK` passes on NULL. With a nullable `kind`, a CORPORATE
     customer silently acquires nationality rows *and* orphan rows become insertable:
     ```
     INSERT … VALUES ('1111…','CORPORATE','FR');  -- ERROR: violates check constraint   ✓
     INSERT … VALUES ('1111…', NULL,       'FR');  -- INSERT 0 1  ← corporate with a nationality
     INSERT … VALUES ('9999…', NULL,       'DE');  -- INSERT 0 1  ← parent does not exist
     ```
     The second row contaminates exactly the query this join table exists for — the FATCA report.
     This is the same NULL-defeats-`CHECK` trap already documented for `status` below; apply it here
     too. Note the composite FK **subsumes** the plain `customer_id` FK — keep one, knowingly.
   - **`admission_decision`**: surrogate PK, index on `idempotency_key` — and **deliberately no
     unique constraint on it**, since a refused applicant may retry with the same key and each
     attempt is its own audit record. Say so, or someone adds one.
   - **🔴 Do NOT denormalise the primary match onto `admission_decision`.** An earlier draft copied
     `restriction`/`connecting_factor`/`triggering_country` onto the parent to preserve an
     all-NULL-or-all-NOT-NULL `CHECK` mirroring the sealed hierarchy — the same technique the `kind`
     discriminant uses. It does not transfer, for four reasons:
     1. **The invariant you actually want is `outcome = 'REFUSED' ⟺ ≥1 match row`** — a *cardinality*
        rule across two tables. PostgreSQL forbids subqueries in `CHECK`, so it is **not expressible
        as a `CHECK` either way**. Denormalising does not buy it; it only buys an intra-row check on
        a copy. Enforce it in the recorder (one insert path) and state it as an application invariant.
     2. **"Enforce agreement with a `CHECK` or a trigger" was wrong** — cross-table agreement is
        **trigger-only**, and the trigger must fire on the **child** too (insert/update/delete on
        `admission_decision_match`), or the copy and the source diverge on the side left unguarded.
     3. **Both stated benefits are cheaper without duplication.** "How many sanctions refusals last
        quarter" is a partial index on the child (`ON …_match (restriction) WHERE ordinal = 0`); the
        single-row read is a one-row join on a PK.
     4. Duplicated state plus a trigger is not a v1 trade. Keep the parent thin.
   - **`outcome` must be `NOT NULL`** — same trap as `status` and as `customer_nationality.kind`
     below: a nullable column makes any `CHECK` over it evaluate to NULL, which **passes**.
   - **🔴 `admission_decision` had no timestamp at all.** It is the table under **5-year retention**,
     the one ALT-9b calls "the audit record the entire compliance design depends on", and the one
     ALT-6's "how many sanctions refusals **last quarter**" query reads. Without a timestamp that
     query is unanswerable and the retention clock is unimplementable. Apply **ALT-1's own standard**,
     which was written for `customer_status_transition` and then not carried to the second audit
     table: **both** `decided_at` (caller-claimed) and `recorded_at` (DB-observed), because a
     caller-supplied time alone makes backdating undetectable.
   - **`subject_type` too.** The refusal metric is partitioned by it (item 8) and the partitioned
     outage is the scenario you would investigate *from this table*. Note the partial index
     `(restriction) WHERE ordinal = 0` does not serve the quarterly query alone — it needs the
     parent's `decided_at` via the join.
   - **`CHECK (restriction IN …)` and `CHECK (connecting_factor IN …)` on `admission_decision_match`
     too**, not only on the policy tables — the `valueOf` argument applies just as hard when an
     investigation reads decisions back years later.
   - **🔴 [superseded by ADR-009 D5] `REVOKE` is NOT the append-only standard — triggers are.**
     ALT-1's "enforced, not asserted" instinct was right and its mechanism was wrong: the app runs
     as the owning superuser, so `REVOKE` is inert, and `CASCADE`, `TRUNCATE` and
     `session_replication_role` each defeat the rest. **Every audit table
     (`customer_status_transition`, `admission_decision`, `admission_decision_match`,
     `customer_admission`) gets `BEFORE UPDATE`/`BEFORE DELETE` row triggers **plus** a
     statement-level `BEFORE TRUNCATE` trigger, all declared `ENABLE ALWAYS`.** Maintenance uses
     `ALTER TABLE … DISABLE TRIGGER` in-transaction — **never** `session_replication_role`.
     **[as shipped in `V6`, two corrections]** (i) the `REVOKE`s are **commented out**, not merely
     "kept as declared intent" — there is no non-owning role to revoke *from*, and
     `REVOKE … FROM PUBLIC` would succeed while doing nothing, which is the ADR-009 D5 mistake in a
     weaker form; (ii) **three non-audit tables are guarded too**, because the audit FKs do not
     protect the onboarding population (see item 0's correction): `customer.customer`
     (`DELETE`/`TRUNCATE` only — `UPDATE` must stay open for activation),
     `customer.customer_nationality` (`TRUNCATE` only), `customer.admission_policy`
     (`DELETE`/`TRUNCATE`). `CustomerAuditAppendOnlyTest` asserts `ENABLE ALWAYS` **from the
     catalog** rather than against a hardcoded table list, so a future migration's trigger is
     covered on the day it lands.
   - **✅ [settled by ADR-009 D6] Retention coupling — `processed_registrations` is NOT pinned.**
     A *refused* decision stands alone on `decided_at`. An *admitted* one does not: it is CDD
     evidence retained to relationship end + 5y, so it gets the `customer_admission` link written in
     step 5 — **on the created branch AND the replay branch** (`ON CONFLICT DO NOTHING`), because
     ALT-9b's accepted "one extra decision row; benign" duplicate otherwise leaves an `ADMITTED` row
     with no anchor. `IN_FLIGHT` writes no link.
   - **🔴 `admission_decision` has NO `customer_id` column at all.** An earlier draft had it
     `NULLABLE, NO FK` with the gloss "refusals have no customer" — which implies admitted rows carry
     one. They do not: ALT-9b establishes the customer row does not exist yet at step 3 in **any**
     scenario. A column that is NULL on every path is **worse than an absent one**: an investigator
     joining `admission_decision → customer` reads NULL as "this was a refusal" and gets it wrong for
     every admitted row. Correlate on `idempotency_key`, which is stable across both outcomes and
     across retries.
   - **`admission_decision_match.ordinal` encodes the ALT-5 total order**, so stored rows replay in
     the order the evaluation produced them. `ordinal = 0` is the primary. Never rely on the heap
     order of a `SELECT` without `ORDER BY ordinal`. *(The `Refused` compact constructor is the
     guarantee; this is belt-and-braces.)*
   - **⚠️ `ordinal` is 0-based while `customer_status_transition.sequence_no` is 1-based**, in the
     same schema. Defensible — one is a list index, the other an aggregate version — but say it once
     in the migration comment, or someone writing ad-hoc investigation SQL gets it wrong.
   - **`restricted_country` / `licensed_country` need `CHECK`s on `restriction` and
     `connecting_factor`**, same reasoning as `status IN (…)`: without them a bad seed surfaces at
     read time as `Restriction.valueOf` blowing up inside the admission adapter, mid-registration.
   - **No FK from `restricted_country.country_code` / `licensed_country.country_code` to anything**,
     and none from `admission_decision_match.triggering_country` — these are policy tables keyed on a
     time-varying set, and the ALT-4/existence-vs-admission split is precisely the decision a FK here
     would undo.
   - **`kind` replaces the generated `type` column.** With per-subtype columns a discriminant is
     needed anyway; a second derived column would be redundant.
   - **The unique indexes are load-bearing for three other decisions** — the translated-exception
     convention, item 7, and ALT-9's conflict semantics.
   - **`CHECK` parenthesisation matters**, and `status` must be `NOT NULL`: with a nullable column the
     biconditional evaluates to NULL and `CHECK` passes, permitting exactly the corrupt row it exists
     to block. The separate `status IN (…)` check matters too — without it a bad write surfaces at
     read time as `CustomerStatus.valueOf` blowing up inside `reconstitute`.
   - Join table over `text[]`/JSONB **for the index on `country_code`** — "every customer holding US
     nationality" is the FATCA report.
   - **No FK from `customer_nationality.country_code` to any country list.** A FK re-introduces
     time-variance at the schema level — de-listing a country would break writes for existing
     customers. (The FK on `customer_id` *is* wanted: no orphan rows.)
   - **`MAXVALUE 999999999`** — at 10⁹ the number silently stops being 10 digits.
   - Append-only on `customer_status_transition`: **`ENABLE ALWAYS` triggers** are the enforcement
     (ADR-009 D5); the ALT-1 `REVOKE` stays as declared intent only.
   - Sort any array/JSONB column holding nationalities (`admission_decision`) — the ordering rule
     applies to audit records read by humans during an investigation.
3. ✅ **DONE 2026-08-10 (`cf54df2`) — jOOQ + item 4's exclusion, one commit.** 39 files under
   `…customer.jooq`: 11 tables (the 10 from V1–V6 plus `flyway_schema_history`), records, POJOs,
   `Keys`, `Indexes`, and a `Sequences` carrying `customer_number_seq` (ALT-7's input). **No
   `Routines` class, and that is correct, not staleness** — all five customer functions are
   `RETURNS trigger`, which jOOQ does not generate. Three deviations from "mirror the account
   execution":
   - **`pojosToString=false`, customer only.** The generated `toString()` concatenates every column,
     which on `customer.customer` is the field set ADR-009 D7 keeps out of logs. **It closes ONE
     surface — D7 owns the inventory** and is deliberately not copied into the pom, because a copy
     drifts. Two traps it does *not* cover, both recorded in the pom comment: the 11 `*Record`
     classes inherit `AbstractRecord.toString()` (renders the row, not controllable from codegen),
     and flipping `pojosAsJavaRecordClasses` would restore an implicit record `toString()` **with no
     build error**. Review also flagged, unfixed and out of scope here: `org.jooq.tools.LoggerListener`
     at DEBUG logs SQL with bind values inlined and formats fetched rows — no `logging.level.org.jooq`
     is set anywhere, so that is one log-level change away from dumping customer rows.
   - **Both executions now read `${env.DB_URL}`**, not a hardcoded `localhost`. Credentials already
     came from `${env.*}`, so the URL was the last thing making "the DB Flyway migrates" and "the DB
     jOOQ introspects" two sources of truth. **Accepted cost:** the profile is named
     `jooq-codegen-local` and no longer enforces local. See backlog **B-14**.
   - **`pojos=true` kept** (per plan) against a suggestion to drop it — item 6 consumes them, the way
     `AccountEventRepository` consumes account's.
4. ✅ **DONE 2026-08-10 — landed with item 3.** `<excludedClasses>` += `…customer.jooq.*`. The
   per-class `…CustomerFlywayConfig` entry stands as-is and the pom carries its reasoning inline.
   - **The per-class form is deliberate and is the better call — do not "tidy" it into a wildcard.**
     PITest cannot un-exclude a class once its package is excluded, so the blanket form would
     swallow the one adapter in this BC with real algorithmic content: the customer-number generator
     (FPE cycle walk + Luhn, item 7). It would leave the mutation gate with nobody editing the file.
     The pom carries this reasoning inline; keep the two in sync.
   - ⚠️ The original rationale still holds for `account.infrastructure.*`: a blanket exclusion is
     safe only while every adapter is dumb. It is the other half of the argument for keeping
     admission evaluation out of the port impl (ALT-5) — branch logic that lands there leaves the
     gate silently. The customer BC now buys that guarantee structurally instead of by convention.
   - **🔴 Consequence for increment 5 item 7:** its claim that `customer.infrastructure.web.*` is
     "subsumed" by `customer.infrastructure.*` is **now false** — there is no such wildcard.
     Corrected in place there.
5. ✅ **DONE 2026-08-10 — ports in `customer.application`, landed with item 12.** Four ports as
   planned: `CustomerStore`, `CountryAdmissionPolicy` (**`AdmissionPolicySnapshot load()`** —
   loads, does **not** decide; ALT-5), `ProcessedRegistrationStore`, `AdmissionDecisionRecorder`
   (its own port — it needs its own transaction boundary, ALT-9b). Plus the carrier types the
   signatures require: `LoadedCustomer`, `AdmissionDecisionId`, and sealed `RegistrationOutcome` →
   `ClaimedRegistration` / `ReplayedRegistration` / `InFlightRegistration`; plus, after review,
   `TransitionOutcome`, `StatusTransition`, `IdempotencyKey`, `Fingerprint` and `Actor`. **Mutation
   gate 308/288 (94%), from 278/258 — survivor count unchanged at 20, so every new mutant is
   killed.** 50 `customer.application` tests; ArchUnit 17/17 and Modulith green *with item 12's carve-out already
   removed*.
   - **⚠️ `mvn pitest:mutationCoverage` as a bare goal does NOT run `test-compile`.** It measures
     whatever is already in `target/test-classes`, so a freshly added test is silently ignored and
     the previous score is re-reported — a false green. Use **`mvn test-compile
     pitest:mutationCoverage`**. Found the hard way: a missing `Actor` length-boundary test looked
     fixed when it was not. `CLAUDE.md`'s Build Commands still documents the bare form.
   - **🔴 `CustomerStore` carries two methods item 6's list never named**, and their absence
     was an omission rather than a decision: **ALT-9b step 5 has to insert the customer**, and
     **ADR-009 / item 0 has to write the `customer_admission` link**. Both are here as
     `insert(Customer, AdmissionDecisionId)` and `linkAdmission(...)`.
     - **The decision id is a parameter on `insert`, not a second call**, for exactly the reason
       `recordTransitionAndApply` is one method and not two primitives: an `ADMITTED` decision that
       never acquires its link falls back to `decided_at` as its retention anchor instead of
       relationship-end, which item 8 already has to *alert* on. Making it unrepresentable on the
       create path is cheaper than detecting it. `linkAdmission` stays separate because the
       **replay** branch links without inserting — the dangerous direction (insert with no link) is
       the one that is closed.
   - **`RegistrationOutcome` is a sealed hierarchy, not an enum plus a nullable id.** The doc said
     "a third outcome, `IN_FLIGHT`"; an enum would have forced a nullable `customerId` on the two
     branches that have none. Same argument `AdmissionDecision` already won on — no `Optional`
     components, and a fourth outcome breaks every call site.
   - **`AdmissionDecisionId` is new, and lives in `..application..`, not `..domain..`.** The domain
     never sees it: `AdmissionDecision` carries no id, and the id exists only to wire ALT-9b step 3's
     transaction to step 5's. Typed rather than a bare `UUID` because it travels next to a
     `CustomerId` and two raw UUIDs swap silently. Same placement argument for `LoadedCustomer` —
     `sequenceNo` is a concurrency token, and the doc already refused to put it on `CustomerState`.
   - **The fingerprint-conflict exception is deliberately NOT created yet.** Unchecked exceptions
     appear in no signature, so nothing here references it; its first thrower is item 6's adapter.
     Creating it now would be a type with zero call sites — the thing item 0 polices for the
     retention type. The port javadoc states the contract without naming the type.
   - **`recordDecision`, not `record`** — `record` is legal as a method name and reads fine, but the
     project's standing rule is to keep restricted identifiers out of identifiers entirely.
   - **🔴 `ProcessedRegistrationStore.isClaimed(IdempotencyKey)` exists because ALT-9b **step 1**
     was otherwise unimplementable.** The first draft of the port set had no method that could do
     the replay pre-check: `register(...)` is claim-or-replay and must stay at step 5 (moving it
     earlier burns a refused key), and `load` needs a `CustomerId` that does not exist yet. Without
     step 1 **every retry runs a fresh admission evaluation and writes a fresh, unconditionally
     committed `admission_decision` + match rows** — which is the in-application half of the
     mitigation for the write-amplification warning ALT-9b already carries. **Existence-only is
     deliberate**: a changed payload under a claimed key still reaches `register` and still trips
     the fingerprint conflict, so the fast path cannot become an authority by accident.
   - **`Actor` is a VO, not a `String`.** `CHECK (actor <> '')` accepts `"   "` and `"\n"`, so a
     whitespace-only actor reaches the audit trail and satisfies the schema. Carries
     `Actor.SYSTEM`; a real subject id arrives with authz (increment 5 item 8).
   - **🔴 `IdempotencyKey` rejects control characters, and this is a security fix, not tidiness.**
     `V4` designates `idempotency_key` as *the* correlation handle between a decision and a
     registration, so it will be logged. There is **no `logback-spring.xml` and no JSON encoder** in
     this project — Boot's default one-line plain-text pattern applies, so a client-supplied `\n`
     **forges audit log lines**. It also rejects surrounding whitespace rather than trimming:
     `"abc "` and `"abc"` are two distinct primary keys, so trimming would silently merge two
     registrations the client meant to keep apart. **Revisit if structured logging lands** — the
     control-character rule stays useful, but the reasoning changes.
   - **`LoadedCustomer.transitionTo(target)` is how a `StatusTransition` should be built.** The
     record alone does **not** make a reversed pair unrepresentable — `new StatusTransition(to,
     from)` still compiles and still satisfies `from != to`; the first draft's javadoc claimed
     otherwise and was wrong in exactly the way this document polices elsewhere. The factory takes
     the source from the loaded snapshot, which also guarantees `from` and `expectedSequenceNo` come
     from the same read.
     - **🔴 `LoadedCustomer` is a `final class`, NOT a record, and reverting that reintroduces a
       reproduced bug.** As a record it held a reference to the **mutable** `Customer`, so
       `transitionTo` read the status *at call time*. The use case calls `activate()` first, so by
       the time the adapter derived the transition the aggregate already said ACTIVE →
       `StatusTransition(ACTIVE, ACTIVE)` → `IllegalStateException` → **500 on every successful
       activation**. Verified by running it, not by reading it. The class captures `loadedStatus` in
       its constructor, which a record cannot do without a redundant third component. Pinned by
       `keeps_reporting_the_loaded_status_after_the_aggregate_is_activated`.
     - **`recordTransitionAndApply` takes the `LoadedCustomer`, not `(id, expectedSequenceNo,
       transition)`.** Passed separately those three can disagree, and the database accepts a
       mismatched set as a perfectly plausible audit row.
     - **⚠️ Open for B-1: `StatusTransition(ACTIVE, ONBOARDING)` is constructible and insertable.**
       Not reachable today — the adapter hardcodes `SET status = 'ACTIVE'` — but it becomes
       reachable the moment B-1 parameterises `to`. The fix is a legality check, and the single
       source of truth for it is `CustomerStatus` in the domain, **not** a second copy of
       `Customer.activate`'s state machine in an application record. With two states and one legal
       transition it is not worth building yet. **Build it with B-1, not before.**
   - **Follow-ups owed, none blocking item 6:**
     1. ~~**`LoadedCustomer` does not mirror the check**~~ — **✅ closed 2026-08-10. The product
        answer is that `ONBOARDING` is the initial state only**, so a periodic review or restriction
        is a separate state and never a return trip. Both halves of `V2`'s residual are now caught
        at read time. The rule and its consequences for the state machine live in **B-1 (f)**, which
        is where the next person to touch this will be looking.
     2. **`INCREMENTAL_DOMAIN_ONLY_BCS` could expire itself.** The real failure mode is not the
        unexercised branch, it is a BC left *in* the set after it grows layers.
        `all_bounded_contexts_have_hexagonal_layers` already computes the layer map, so it can fail
        when a listed BC has both outer layers. Turns "remember to remove it" into a check, which is
        that file's whole thesis.
     3. **`CLAUDE.md` Build Commands documents the bare `pitest:mutationCoverage`** — see the false
        green above.
     4. **🔴 `GlobalExceptionHandler` echoes `e.getMessage()` into the 500 body.** Pre-existing, and
        **newly load-bearing**: four `customer.application` types were deliberately routed to that
        handler this increment. Today's messages are benign constants, but the *pattern* is not —
        any `DataAccessException` reaching it puts SQL text and constraint names in an HTTP response
        at a regulated bank. `CustomerRowCorruptException`'s "never echo row contents" discipline now
        applies to every `IllegalStateException` in this package, and a handler is the wrong place to
        rely on discipline. Track it; do not leave it as "later".
     5. **`CustomerRowCorruptException` is missing from `KafkaErrorConfig`'s non-retryable list.**
        The four types moved to `IllegalStateException` are covered for free — `IllegalStateException`
        and `IllegalArgumentException` are already registered as non-retryable, so HTTP and Kafka
        agree without anyone coordinating it. The corruption type is not, so a future customer
        consumer would retry it under `ExponentialBackOff` against a row that cannot heal. Add it
        together with the dedicated 5xx mapping its javadoc already says it is owed.
     6. **The mandatory fallback `SELECT` is protected only by prose** — in ALT-9, in the
        `InFlightRegistration` javadoc, and in item 6's list. Prose is what gets optimised away. The
        control that holds is a Testcontainers test racing two transactions on one key and asserting
        the loser gets `ReplayedRegistration`. Land it **with** the adapter, not after.
   - **🔴 `IdempotencyKey` and `Fingerprint` are VOs, and deferring them was the wrong call.** The
     first draft of this item shipped `register(String idempotencyKey, String fingerprint, …)` and
     argued the VOs were an increment 5 web-boundary decision. Two things killed that argument in
     review. **`IdempotencyKey` is used by two ports here** (`ProcessedRegistrationStore` and
     `AdmissionDecisionRecorder`), so deferring it to the controller puts the decision in the wrong
     layer. And **`V5`'s `CHECK (fingerprint ~ '^[0-9a-f]{64}$')` is not the backstop it looks
     like**: a client that derives its key by hashing its own request — normal practice, not an
     exotic case — supplies a 64-lowercase-hex key, which passes both the `CHAR(64)` width and the
     shape check. A consistently swapped adapter would then key the table on the payload hash and
     verify on the key, i.e. **deduplicate by payload**: two applicants submitting identical details
     replay each other, and the second is handed the first's `customer_id`. A silent cross-customer
     identity leak in a KYC flow, with no constraint firing and no exception. The VOs make it
     unrepresentable. `Fingerprint` also gives the hex shape a place to fail readably instead of
     arriving as a constraint violation from inside the adapter.
   - **🔴 `recordTransitionAndApply` returns `TransitionOutcome {APPLIED, SUPERSEDED}`, not the
     `int` rowcount ALT-8 specifies.** ALT-8's own text calls the rowcount "the authority" on who
     activated the customer, and then hands it over untyped: `== 1` and `> 0` both compile and
     differ. The heavier argument is **observability** — `CommandMetricAspect.classify` keys on
     typed results (`TransactionStatus`, `ReservationResult`), so with an `int` the customer BC's
     eventual equivalent has nothing to classify and an activation contention storm renders as
     ordinary committed traffic. **An enum, not a sealed hierarchy**: neither case carries data, and
     `TransactionStatus`/`ReservationResult` are the account precedent for exactly this. Sealed
     earns its place on `RegistrationOutcome` only because one branch carries a `CustomerId`.
     **The adapter maps rowcount → outcome; ALT-8's Java snippets still say `int`.**
   - **🔴 `StatusTransition(from, to)` replaces the two adjacent `CustomerStatus` parameters**, and
     this was the *stronger* swap hazard, not the `String` one. Today a swap is caught by accident:
     `ck_customer_status_transition_starts_from_onboarding` requires `from_status = 'ONBOARDING'` at
     `sequence_no = 1`, and activation is always first. **The moment B-1 lands SUSPENDED and
     transitions occur at `sequence_no >= 2`, that check stops engaging** — `from <> to` is
     satisfied by a swap — and the CTE applies the wrong status while writing a plausible audit row.
     The record carries the `from != to` invariant, inside the mutation gate.
   - **🔴 `LoadedCustomer` rejects `ACTIVE` with `sequenceNo == 0`, which closes `V2`'s recorded
     residual at read time.** `V2` states outright that a direct `UPDATE customer.customer SET
     status = …` commits with no transition row and that nothing in the schema prevents it.
     `LoadedCustomer` is the **only** object holding status and transition sequence from one
     snapshot, so it is the unique place that bypass is detectable. *Consequence for any future
     backfill:* importing `ACTIVE` customers without synthesising their transition rows now fails on
     every load — which the audit obligation requires of such a backfill anyway. Carried by the
     `Active customer carries no status transition` corruption message rather than by a javadoc.
   - **`findState` is named `findStateAfterSupersededTransition`.** A javadoc sentence was the only
     thing stopping a future caller using it where `load` is required, which reintroduces the
     two-snapshot lost update. Everywhere else in this port the constraint is structural; the name
     puts it at every call site.
   - **🔴 `CountryAdmissionPolicy.load()`'s javadoc originally over-claimed a compliance control.**
     It said a recorded decision "can never document a policy that was not the one applied". Checked
     against `V3`: `assert_policy_version_advances` enforces **monotonicity only**, and nothing
     forces an edit to the policy tables to advance the version. The single statement buys snapshot
     consistency, not version-identifies-ruleset. Rescoped, with a pointer to V3's residual. Worth
     generalising when extracting the ADR: an overstated control in a compliance path is exactly
     what gets leaned on during an audit.
6. ◐ **Adapters** in `customer.infrastructure.db`. **⚠️ Item 5 landed the ports, so implement against
   the signatures in `customer.application`, not against the sketches below — three of them moved,
   and the reasons are in item 5.**
   **✅ `CustomerStore` landed 2026-08-11 as `CustomerRepository` + `CustomerToRowMapper` /
   `CustomerRowToStateMapper`, reviewed 3x.** 525 tests green; mutation 338 killed of 358 (94%),
   with **zero survivors in the two mappers** — quote it that way, not "across the new classes":
   `CustomerRepository` is excluded and contributes no mutants, so a zero-survivor claim over it
   is vacuous. Still owed: `ProcessedRegistrationStore`, `CountryAdmissionPolicy`,
   `AdmissionDecisionRecorder`.
   - **🔴 The port was renamed `CustomerRepository` → `CustomerStore`, and the adapter takes the
     freed name.** Item 5 shipped the port as `CustomerRepository`, which inverts the account BC's
     convention: the port is a `*Store` / `*Reader` in `application` (`AccountEventStore`,
     `ProcessedTransactionStore`) and the adapter is the `*Repository` in `infrastructure.db`
     (`AccountEventRepository`). The first draft of this adapter worked around the clash by calling
     itself `JooqCustomerRepository` — a technology prefix invented to dodge a naming mistake, in a
     package where the ArchUnit rule already confines jOOQ. Fixed at the source instead.
     - **State the convention narrowly or it is false**: it is *not* "ports are `*Store`" — account
       also has `AccountEventPublisher`, `AccountBalanceProjector`, `AccountLoader`, and
       `AccountBalanceReader`'s adapter is `AccountBalanceReadModel`, not a `*Repository`. The rule
       that holds without exception is **`*Repository` names an adapter in
       `..infrastructure.db.repository..`, never a port**; ports are named for what they do. Under
       that reading the BC's other three ports were already clean.
   - **`CUSTOMER` is bound to a private constant** because codegen emits `CUSTOMER_`: the schema and
     its central table are both named `customer`. Do not "fix" the underscore in the generated code.
   - **🔴 STANDING RULE: a container test MINTS its fixtures. A customer number, an email or an id
     written as a literal is a defect.** Every test must pass alone and in any order — a suite that
     holds only in the order it happens to run in is not a control.
     - **Why this BC in particular:** `AbstractContainerTest` starts one Postgres per JVM in a static
       initialiser and never stops it, and `CustomerSchemaConstraintsTest` /
       `CustomerAuditAppendOnlyTest` commit through `SchemaProbe` on their own connections *outside*
       the `@JooqTest` transaction — and **cannot** clean up, because the audit tables are
       append-only. Their rows are therefore visible to every later test in the run.
     - **How it was found:** this adapter's tests first used `ada@example.com` / `0000000018` and
       passed **only because they ran first**. Renaming the class reshuffled Surefire's `filesystem`
       default order and turned 13 of 15 green tests red. Nothing was wrong with the rename.
     - **The fix is minting from a UUID, not partitioning ranges.** A first attempt reserved a
       numeric range clear of the siblings' literals; that is still coordination — it encodes one
       test file's knowledge of another's constants, and it decays the moment someone adds a fixture.
       Minted values need no agreement between files.
     - ✅ **DONE 2026-08-14 — the minting is hoisted and no container test holds a literal
       fixture.** `CustomerFixtures.mintCustomerNumber()` / `mintEmail()` are the one home; a fourth
       class costs a static import rather than a fourth copy. What it covered:
       `CustomerSchemaConstraintsTest`'s 24 numbers and 24 emails plus `INDIVIDUAL_ID` /
       `CORPORATE_ID`, `CustomerAuditAppendOnlyTest`'s pair plus its three UUIDs, and
       `CustomerRepositoryTest`'s private copies of the minter.
       - **🔴 Only TWO of the three classes actually commit, and the distinction is the whole
         hazard.** `CustomerSchemaConstraintsTest` and `CustomerAuditAppendOnlyTest` drive
         `SchemaProbe`, which opens its own connections from the `DataSource` and so escapes the
         managed transaction. `CustomerRepositoryTest` is `@JooqTest`, which is meta-annotated
         `@Transactional` and rolls back. An earlier revision of this entry called all three
         "committing"; it was wrong in the safe direction, but the entry's whole job is to name which
         classes are dangerous.
       - **Proven, not assumed** — the standing rule demands the run that reshuffles order:
         **536 tests green in five class orders** (default, `reversealphabetical`, three `random`),
         **plus two runs with JUnit method order randomised**
         (`-Djunit.jupiter.testmethod.order.default=…MethodOrderer$Random`). *The method-order axis
         is the one this change created and the one `surefire.runOrder` does not reach — that flag
         shuffles classes only, while the fixtures moved from effectively per-class to genuinely
         per-method. Confirm the randomiser engaged by diffing `testcase name=` order across the
         surefire XMLs; a green run proves nothing if the order never changed.*
       - **The ids are minted per test, not per class, which retired every `ON CONFLICT DO
         NOTHING` in both setups.** Those clauses existed only because static ids made the second
         `@BeforeEach` collide with the first. With minted ids they are unreachable, and a
         `DO NOTHING` that can never fire is one that would hide a defect leaving a row absent.
       - **🔴 Three call sites are load-bearing and must not be "cleaned up" into plain mints:** the
         non-canonical-email test needs an uppercased address, and the distinct-constraints test
         needs the setUp individual's *email* and *number* respectively. They are wired through
         `corporateWithEmail` / `corporateWithNumber` for exactly that reason. The build catches a
         mistake here — `SchemaProbe.expectViolation` fails when the database accepts the row — so a
         green suite is evidence these still trip their constraints.
       - **`CustomerFixturesTest` pins the minter against the production VO.** The check digit is
         computed in test code and verified by `CustomerNumber`; the schema tests insert the result
         as raw SQL, so a disagreement would pass every column `CHECK` and surface only as rows the
         aggregate cannot read back. *Distinctness is asserted over two draws, not a large sample:
         the body is eight digits, so a thousand draws collide about once in two hundred runs — that
         is a flaky test, not a control. Two draws still catch a minter that stopped varying.*
       - **🔴 STANDING RULE the volume increase creates: any new assertion over these tables must be
         scoped by a minted id, never a global count.** Per-method minting removed the accidental
         per-class deduplication, so `CustomerAuditAppendOnlyTest` now commits 23 customers, 23
         transitions, 46 decisions, 23 matches and 23 links per JVM run instead of one of each, and
         none of it is cleanable. Every count assertion in the tree is id-scoped or a catalog query
         today; a `SELECT count(*)` over a whole audit table would be green now and wrong later.
       - **Residual, accepted:** CI still runs a bare `mvn clean install`, so nothing *enforces*
         order-independence. `-Dsurefire.runOrder=random` with the seed logged is the lever, and the
         method-order flag above belongs with it; pinning `alphabetical` is the wrong answer, because
         it hides these rather than surfacing them.
     - **Prove it, do not assume it:** `-Dsurefire.runOrder=reversealphabetical` is the run that puts
       a new class *after* the committing ones, and `random` is the one that finds what neither
       fixed order does. *(Detection is still missing in CI, which runs a bare `mvn clean install`.
       `-Dsurefire.runOrder=random` with the seed logged is the lever; pinning `alphabetical` is the
       wrong answer, because it hides these instead of surfacing them.)*
   - **🔴 `insert` carries `@Transactional(propagation = MANDATORY)`.** Three statements, and a
     customer that commits without its `customer_admission` link is the wrong retention anchor — the
     exact state the decision id parameter exists to prevent. MANDATORY makes ALT-9b step 5's
     boundary a runtime failure instead of a convention, and it pairs with the recorder's "the
     calling method must not be `@Transactional`" to pin the orchestrator into its only correct
     shape. The ArchUnit rule permits it (location-only); its `because(...)` and `CLAUDE.md` said
     "read-side `readOnly=true`" and were widened to say so.
   - **🔴 The two unique violations have opposite transaction semantics, and this is recorded on
     `CustomerNumberCollisionException` because nothing at the call site shows it.** The email
     conflict is absorbed by `ON CONFLICT (email) DO NOTHING` and reported from a rowcount, so the
     *connection* stays usable. A `customer_number` collision is *not* absorbed: Postgres raises
     23505 and aborts the transaction before Spring translates it, so a later statement dies with
     "current transaction is aborted".
     **🔴 Neither is recoverable in place, and the difference is diagnostic quality, not
     recoverability.** Throwing across the `MANDATORY` boundary marks the caller's transaction
     rollback-only (`globalRollbackOnParticipationFailure` defaults to true), so even after the
     email conflict the caller can read but can never commit — it gets `UnexpectedRollbackException`
     at the boundary. Read the healthy/aborted split as *what the next statement tells you*, never as
     licence to catch and continue. ALT-9b step 5 already rolls back on an email conflict, which is
     the correct shape; this note exists so the orchestrator is not written the other way.
   - **🔴 The collision exception chains no cause, deliberately.** The driver renders a unique
     violation as `Key (customer_number)=(...) already exists`, and `GlobalExceptionHandler` logs the
     whole chain *and* puts `getMessage()` in the response body. **Accepted cost:** a PK collision on
     `id`, and any unique constraint added later, land on the same constant message and the same
     alert. `PSQLException.getServerErrorMessage().getConstraint()` returns the constraint name with
     no values and would fix that; declined here to keep the driver out of the adapter, so take it
     the day a second minted identifier exists.
   - **🔴 The row→state mapper reports *every* value-object rejection as `CustomerRowCorruptException`,
     not only nulls and unknown enums.** Column checks are not enough and the gap is specific: a Luhn
     check digit, ISO membership and the email shape all pass `^[0-9]{10}$`, `^[A-Z]{2}$` and the
     canonicalisation check, and are rejected by the VO. Left alone they surface as the
     `Invalid*Exception` family — which is invisible today (everything falls to the catch-all) and
     becomes a **422 blaming the applicant for a corrupt file, with no corruption alert**, the day
     increment 5 item 5 lands `CustomerExceptionHandler`.
     - `NullPointerException` and `ClassCastException` are rethrown unchanged: those are defects in
       the mapper, and reporting one as corruption pages someone about clean data.
     - **Follow-up, owed with `CustomerExceptionHandler`:** give the six `Invalid*Exception` a common
       supertype. The catch is `RuntimeException` only because no such type exists; the handler needs
       exactly the same taxonomy, so build it once, there.
   - **🔴 The "messages name columns, never values" rule is now pinned by assertions, not by the
     mutation score.** PITest's default mutators do not touch string literals and cannot remove a
     `catch`, so *zero survivors says nothing about message content*. Re-adding a chained cause kept
     every test green until `withMessage(...)` + `getCause() == null` were asserted explicitly.
   - **`linkAdmission` targets `ON CONFLICT (customer_id, decision_id)`**, not a bare `DO NOTHING`,
     so a decision already anchored to a *different* customer still hits `uq_customer_admission_decision`.
     Rowcount 0 is then ambiguous between "already linked" and "customer absent", so it re-checks
     existence and raises corruption — one PK lookup on a path that only a replay reaches.
   - **`insertAdmissionLink` (create path) translates nothing, and the reason is the composite FK,
     not the UUIDs.** `uq_customer_admission_decision` is indeed unreachable with two freshly minted
     identifiers — but `fk_customer_admission_decision (decision_id, outcome, subject_type)` fires
     whenever the decision's subject type disagrees with the customer's kind, which a use-case bug or
     a crossed replay reaches regardless. Left untranslated because the key exposes UUIDs and a type
     name, no personal data, and the driver message is the best diagnostic available.
   - **🔴 A mutation gate does not measure a switch over enum constants.** `ofUpdatedRows` generates
     exactly **one** mutant (`NULL_RETURNS`) no matter how many arms it grows: the arms compile to
     `GETSTATIC` and no default mutator touches them, so swapping `APPLIED` and `SUPERSEDED` — or
     folding `default` into `APPLIED` — leaves the gate at 100%. So do **not** justify moving the
     lattice out of the adapter as "keeping it measured"; the true reason is that it becomes
     unit-testable in milliseconds rather than behind a container, and `TransitionOutcomeTest` is the
     only thing pinning the mapping. Same family as the `FRECORD` blindness above and as the
     string-literal blindness in the next bullet: **check the mutant list, not the percentage.**
   - **PITest, two changes — see the pom, which carries the reasoning:** `CustomerRepository` is
     excluded per class (what is left in it is SQL; the rowcount lattice moved to
     `TransitionOutcome.ofUpdatedRows`), and the `excludedTestClasses` glob
     became a **raw regex** `~…\.repository\.[^.]*`. **The glob was silently wrong:** PITest turns
     `*` into `.*`, so `…db.repository.*` crossed into `…repository.mapper` and removed 41 mapper
     mutants from the gate. `[^.]*` is package-local *and* fail-closed — a container test added to
     that package later is excluded without anyone editing the pom. Use `*` inside the class part,
     never `+`, which PITest escapes to a literal even in regex mode.
     - The `account.infrastructure.db.repository.*` entry above it has the same defect and is
       harmless only because `account.infrastructure.*` already sits in `excludedClasses`. Said in
       the pom so it does not read as an oversight.
   - `TransitionOutcome recordTransitionAndApply(id, expectedSequenceNo, StatusTransition,
     occurredAt, actor)` — the **single** data-modifying-CTE statement from ALT-8. One method, not
     two: two separable primitives would let a future path update status without writing the audit
     row. **The adapter maps the UPDATE's rowcount to the outcome** — 1 → `APPLIED`, 0 →
     `SUPERSEDED`. Nothing else may produce a rowcount above 1 (the `WHERE` is on the primary key),
     so treat anything else as a bug rather than folding it into `APPLIED`.
   - `void insert(Customer, AdmissionDecisionId)` and `void linkAdmission(CustomerId,
     AdmissionDecisionId)` — the registration writes this list originally omitted. `insert` writes
     customer + nationalities + the `customer_admission` link in one call; `linkAdmission` is the
     replay branch's `ON CONFLICT DO NOTHING` link.
   - `Optional<LoadedCustomer> load(CustomerId)` — aggregate **and** `sequenceNo` from **one SQL
     statement** (sequence as a scalar subquery), per ALT-8. Two statements reintroduce the lost
     update through the reader. `sequenceNo` is `COALESCE(MAX(sequence_no), 0)`; the record now
     **rejects `ACTIVE` at 0**, so a bad subquery surfaces as corruption rather than silently.
   - `Optional<CustomerState> findStateAfterSupersededTransition(CustomerId)` — the rowcount-0
     re-read path only, and now named so at every call site.
   - **Unique-violation translation must discriminate by constraint**, not lump them: an `email`
     violation is a client-fixable **409**; a `customer_number` violation is a server-side FPE
     collision the client cannot act on → **500 + alert**, and it should be near-impossible (ALT-7).
   - Admission adapter: **one round trip** returning an `AdmissionPolicySnapshot` — both tables plus
     the list-level `policy_version`, read in the **same statement**, or the recorded decision
     documents a policy that was never the one applied. **It returns rows and decides nothing**
     (ALT-5): no quantifier logic, and specifically **no matching in the `WHERE` clause**.
   - Registration store: the CTE pattern **copied from `ProcessedTransactionRepository`, including
     the fallback `SELECT`**.
7. ✅ **UNBLOCKED 2026-08-10 — but runs AFTER item 8.** `FpeCustomerNumberGenerator` (ALT-7) —
   sequence → FPE → cycle-walk loop → Luhn.
   `customer.customer_number_seq` already exists (`V1`, `MAXVALUE 999999999`, pinned by
   `the_customer_number_sequence_is_bounded_to_a_nine_digit_body`), so the DB half is done.
   **NOT in `infrastructure/id/`**: it needs `nextval`, and `jooq_only_used_in_db` permits `org.jooq..`
   only in `..infrastructure.db..`. **No retry inside a transaction**; mint before the registration
   transaction opens.
   **Both blocking decisions were made on 2026-08-10 — see ALT-7 for each in full:** shape **(c)**
   (minting rule in `..domain..`; `CustomerNumberSequence.next()` → `..infrastructure.db..` and
   `CustomerNumberObfuscator.obfuscate(long)` → `..infrastructure.id..` as ports) and **env var now, secret store as target state, arriving through one seam**.
   The superseded framing follows, kept because it records *why* the options were rejected:
   - **Shape (a) or (b) from ALT-7** — whole generator in `..infrastructure.db..`, or FPE + Luhn in
     `..infrastructure.id..` behind a `CustomerNumberSequence` port implemented in
     `..infrastructure.db..`. Free to settle now, and cheap; ALT-7 warns that leaving it open invites
     the next session to rule-lawyer around the ArchUnit rule with `JdbcTemplate`. **Note the item-4
     interaction:** under (a) the whole generator sits in `..infrastructure.db..` and is mutated
     (nothing excludes it — that is the point of the per-class exclusion); under (b) the maths sits
     in `..infrastructure.id..` and is mutated there instead. Either way the gate covers it, so pick
     on architecture grounds, not on mutation-coverage grounds.
   - **Key provenance and custody** — the project has no secret store. ALT-7 flags the sequencing
     contradiction plainly: this item sits *inside* increment 4 while its own blocker is "settle
     before item 7". Sequence it explicitly, or move item 7 behind item 8.
8. ⬆️ **Observability (ALT-6) — RUNS BEFORE ITEM 7. Confirmed 2026-08-10**; the earlier "consider"
   is now a decision. Replaces the fail-fast boot the cache removal deleted. When this was written, an
   unseeded policy table was a hypothetical failure mode. After `e1f39da` it is the *actual, current*
   state of the database — `licensed_country` is empty by design, so every applicant is refused and
   the refusal is invisible to the client by the tipping-off rule. `V3`'s own comment now says these
   assertions and this metric "are a prerequisite for serving traffic, not a nicety". Nothing in
   `src/main/resources` defines `customer.admission.expected-factors.*` yet.
   - Admission outcome counter tagged by **`restriction` + `connecting_factor` + subject type**.
     **`triggering_country` must NOT be a label** — it is PII-adjacent (B-2) and unbounded-ish.
   - Alert on refusal ratio, **partitioned by subject type**.
   - Startup assertion that each table covers every factor in its **own** declared property
     (`…expected-factors.restricted` / `…expected-factors.licensed`) — never inferred from the
     tables, and never one shared list. ALT-6 explains why inference is circular and why a shared
     list is either unsatisfiable or silently toothless.
   - **Plus a named seed-integrity assertion on the US triple** (ALT-6): the factor-level check
     cannot detect the loss of any single row, including the FATCA `NATIONALITY` row the whole
     admission design turns on. *(What exists today is the **CI** half —
     `CustomerSchemaConstraintsTest.the_us_person_rule_is_seeded_as_three_rows`, a Testcontainers
     test. `V3` has no `DO` block, so the seed has **no production-time coverage at all**. The
     runtime assertion owed here is therefore the first and only check that will ever run against a
     live database — and the only thing that would catch `V3`'s out-of-band-edit residual; see
     ALT-6 standing rule 3.)*
   - **🔴 [added 2026-08-08] An unlinked `ADMITTED` decision is an alertable condition** — an
     ADR-009 constraint with no owner until now. ALT-9b treats the crash-between-steps-3-and-5 case
     as benign ("accurate, not corrupt"), and structurally it is; but an `ADMITTED` row with no
     `customer_admission` link falls back to `decided_at` as its retention anchor instead of
     relationship-end + 5y, which is a *different* retention class arrived at by accident. It must
     be visible, not merely tolerated.

   - **🔴 [added 2026-08-13] Item 15 created this BC's first emission points, and there is no
     primitive to emit through.** `ActivateCustomer` ships with no metric and no log, which is not an
     oversight to fix with an annotation: `@CommandMetric` / `CommandMetricAspect` live in
     `account.application.metric`, `account` is a CLOSED Modulith module, and
     `classifyException` is hardcoded to account exception types. **Item 8 has to build a second
     instrumentation primitive**, and these are the three signals waiting for it:
     1. **`TransitionOutcome.SUPERSEDED`** — the only place in the system that knows two actors raced
        on one KYC activation and that just one of them reached the audit trail. ALT-8 fixed the
        *instant*; the fact that the race happened is currently unrecordable.
     2. **Both `CustomerRowCorruptException` throws in the activation path** — the exception's own
        javadoc promises a corruption alert. Today they are an unlabelled 500, indistinguishable from
        a `NullPointerException` in the logs.
     3. **`CustomerNotFoundException`** — needs to bucket as a client 404, not as an error, or the
        refusal-ratio alerting in this same item is polluted by ordinary not-founds.

   Without this, a mis-seeded table is a silent onboarding outage — the tipping-off rule guarantees
   no informative client signal. **[Q2] And the outage can be *partial*:** `licensed_country` seeded
   with only `RESIDENCE` rows refuses every corporate while individuals succeed, which a global
   assertion and an aggregate alert both miss. The per-factor and per-subject-type refinements exist
   for that case specifically.
9. ✅ **DONE 2026-08-06 — landed with item 1**, then reshaped by `f767909`.
   `AbstractContainerTest` lives in `org.girardsimon.wealthpay.testsupport` and is **BC-neutral**.
   Each BC subclasses it with its own `@Import` —
   `account.infrastructure.db.repository.AbstractAccountContainerTest` and (since `e1f39da`)
   `customer.infrastructure.db.repository.AbstractCustomerContainerTest` — so schema selection is
   the production bean's and a BC's tests migrate only its own schema.
   - **[corrected 2026-08-08] The "container plus three `spring.datasource.*` registrations, nothing
     else" description is out of date on every detail.** As it stands now: the field is `POSTGRES`,
     `protected static final`, started in a **static initialiser** rather than via
     `@Testcontainers`/`@Container` (that extension stops the container in `afterAll`, which
     desynchronises from Spring's configuration-keyed context cache and yields "connection refused"
     against a dead port); the container runs `max_connections=300` with `fsync=off` **restored**
     (`withCommand` replaces the constructor's array wholesale, so omitting it silently re-enables
     real fsyncs for the whole suite); and `configureDatasource` registers **five** properties — the
     three datasource ones plus `hikari.minimum-idle=0` and `idle-timeout=10s`, so a finished
     context's pool drains instead of pinning connections per cached context.
   *(Unaffected as predicted: `OutboxCleanupMigrationTest` builds `Flyway` programmatically.
   `flywayDefaultDdlModeProvider` does exist in Boot 4.0.2 — a top-level `@Bean` on the **outer**
   `FlywayAutoConfiguration`, so it survives the back-off — and it takes `ObjectProvider<Flyway>`,
   so N beans are fine. It only feeds Hibernate's `ddl-auto` detection, and there is no JPA here.
   An earlier revision of this line claimed the bean did not exist; that came from a shell search
   that silently matched nothing. Verified with `javap`.)*
10. ◐ **Repository tests — the schema half landed with item 2; the adapter half needs items 6–7.**
    ✅ Already covered by `CustomerSchemaConstraintsTest` / `CustomerAuditAppendOnlyTest`, against a
    container, without any adapter: the `ACTIVE` ⟺ `activated_at` biconditional **in both
    directions**; `activated_at >= registered_at`; the discriminant `CHECK` on a mixed
    individual/corporate row; email and number conflicts reported by **distinct** constraints (the
    input to item 6's translation rule); the 1–10 nationalities rule including the
    move-the-last-one-away `UPDATE`; corporates cannot acquire nationalities; refused decisions
    cannot be anchored to a customer; match ordinals contiguous from zero; append-only under
    `UPDATE`/`DELETE`/`TRUNCATE`/`session_replication_role`.
    ⬜ **Still owed, because each needs a real adapter:** round-trip an ACTIVE customer with 2
    nationalities and a **leading-zero** number; unique-violation **translation** (the constraints
    are pinned, the mapping to 409-vs-500 is not); **double-activate sequentially** (rowcount 1 then
    0 — no threads, no barrier, no fight with the test transaction); a
    **stale-`expectedSequenceNo`** call returns rowcount 0; FPE injectivity over `[1, 10⁹)`.
    **Admission adapter — now a thin load, so its tests are thin too** (ALT-5 moved evaluation to a
    pure function): `load()` returns both tables plus `policy_version` from one statement; a
    multi-match refusal **round-trips every match, `ORDER BY ordinal`, primary at 0**. That is all.
    - **The admission *rules* are tested where they now live** — pure `evaluate(subject, snapshot)`
      unit tests, shipped **with item 14**, no container, mutation-covered: the `RESIDENCE`-but-not-
      `INCORPORATION` partitioned-outage case, the **US-incorporated corporate refused** on
      `RESTRICTED_PERSON`/`INCORPORATION` (the entity limb), evaluation order, deny-ANY vs
      allow-membership. An earlier draft put these here, against a container, because the adapter was
      going to host the logic.
    - **🔴 Do NOT write a "run it twice in the same suite" test for the ordering.** It provably
      cannot fire: `ImmutableCollections.SALT` is initialised **once per JVM launch**, so repeated
      iteration *within* a run yields identical order — an unsorted implementation agrees with
      itself and the test passes green while the defect is live. Verified empirically on the JDK in
      this repo: three in-JVM iterations identical, two JVM launches different. The real guard is the
      `RefusedDecision` compact constructor plus its domain tests (shipped in `fccaa7e`); this repository
      test only needs to prove `ordinal` survives the round trip.
11. **ArchUnit**: only `..infrastructure.db..` may call `Customer.reconstitute` (with the
    DoNotIncludeTests note in the rule comment).
12. ✅ **DONE 2026-08-10 — landed with item 5**, at the moment the ports made `customer.application`
    non-empty, not at the end of the increment. Both outer layers are now `layer` rather than
    `optionalLayer` for every BC, so an accidentally-missing layer is caught again.
    - **The set is emptied, the mechanism is kept.** `INCREMENTAL_DOMAIN_ONLY_BCS` is now `Set.of()`
      with a comment saying why it is empty. The next BC starts domain-only too, and an exemption
      that has to be re-invented under time pressure is one that gets replaced by weakening the rule
      for everyone. Do not delete the field.
13. **🔴 Write the admission ADR here, not at the end of increment 5.** This increment is where
    `Restriction` and `ConnectingFactor` become `CHECK` constraints **and** seeded policy rows —
    after that, being wrong costs a migration plus a re-seed (item 2's own warning). It is also the
    point of maximum risk to the reasoning itself: this WIP file is untracked and declares itself
    disposable, yet it is currently the **only** record of the polarity argument, the tipping-off
    rationale, the FATCA-vs-Reg S split, and the Q1 counter-argument. `git clean -fd` loses all of
    it. See *Doc updates owed* for the contents.
    - ✅ **The home authorisation jurisdiction is SWITZERLAND** (FINMA), settled 2026-08-10 — write
      it into the ADR explicitly, since every licensing claim in ALT-5 is relative to it. Carry
      with it: CRD VI Art. 21c constrains **us** (no EEA passport; licensed branch per member state
      from 11 Jan 2027, grandfathering window already closed on 11 Jul 2026); the Swiss tipping-off
      citations (**AMLA Art. 10a**, **Banking Act Art. 47**) in place of "most jurisdictions"; and
      the FATCA **Model 2 → Model 1** conversion effective 1 Jan 2027.
    - **🔴 This ADR is no longer the only one owed.** The Switzerland answer falsifies ADR-009's
      stated premise that the jurisdiction did not matter (retention is **10** years under Swiss
      AMLA Art. 7(3), not 5; the data-protection regime is **revFADP**, with GDPR's parallel
      application via Art. 3(2) an open question — see the banner, **do not state this as "FADP, not
      GDPR"**; and the refused-applicant anchor loses its AMLR basis). That needs an
      **ADR-009 amendment or a superseding ADR-010** — not a silent edit to a committed ADR whose
      reasoning is correct for the jurisdiction it assumed. Sequence it with this one; they share
      the same input.
    - **Convert the draft-history narrative into *Rejected alternative* entries while extracting.**
      Three review passes have accumulated ~40 lines of "an earlier draft of this document said X"
      plus two paragraphs of advocacy aimed at future reviewers. That is the right *content* in the
      wrong *frame*: "an earlier draft of this WIP file" means nothing to an ADR reader who never saw
      it. Reframed as `Rejected: X — why not`, every argument survives and the self-referential
      scaffolding drops. **Do this during extraction, not now** — the history is still load-bearing
      while this file is the only carrier.
14. **🔴 The admission evaluation function — scheduled 2026-08-11 because it was scheduled NOWHERE.**
    `AdmissionDecision evaluate(AdmissionSubject, AdmissionPolicySnapshot)` (ALT-5) does not exist in
    `src/`. Until now the plan named only *where its tests go* (item 10 and increment 5 item 6), never
    where the function lands — it was implied by increment 5 item 1. **It is the most
    compliance-critical branch logic in this BC** — deny-ANY over restrictions, allow-membership over
    licences, the ordering, the `RefusedDecision` assembly — and it was the piece ALT-5's whole
    argument exists to keep out of an adapter. Implied work is the work that slips.
    - **Runs early: it has ZERO dependencies.** No port, no adapter, no schema, no Spring. Every
      input and output type shipped in increment 3 (`fccaa7e`). It could have been built then, and
      it must exist before increment 5 item 1, which cannot register anyone without it.
    - **Placement — settle it when building, and only these two are on the table:** a stateless
      domain service (`..domain.model..`, pure, ArchUnit purity rule guards it) or a method on
      `AdmissionPolicySnapshot` (`snapshot.evaluate(subject)` — the policy evaluating a subject
      against itself, and `AdmissionSubject` already carries behaviour by deliberate decision). Do
      **not** put it in `..application..` merely because the use case calls it, and never in an
      adapter — ALT-5 settled that and gave both reasons.
    - Its tests are already specified in increment 5 item 6: pure, no mocks, no container, and
      mutation-covered. Bring them with the function rather than leaving them an increment behind.
15. ✅ **DONE 2026-08-13 — `ActivateCustomer`, moved here from increment 5 item 2 and taken ahead of
    the rest of item 6.** `CustomerApplicationService.activate(CustomerId, Actor)` plus
    `ActivationResult` and `CustomerNotFoundException`. **532 tests green; ArchUnit 17/17 and
    Modulith green; mutation 368 mutants, 347 killed (94%), customer BC 129/126 (98%)** — measured
    after the port change below, not before it. Against increment 3's committed baseline (525 tests,
    338 of 358) that is +7 tests and +9 kills. *Mutant accounting, since it is easy to get backwards: the count is a function of
    `targetClasses` alone — `excludedTestClasses` only flips a mutant's status to `NO_COVERAGE`. Two
    `orElseThrow` sites moved from the measured `CustomerApplicationService` into the excluded
    `CustomerRepository`, which is what moved the count.*
    - **Why it moved: the ports had zero callers, and that is what produced item 5's 🔴.**
      `LoadedCustomer` prescribed a call order in javadoc that nothing executed, so an aggregate
      mutation between load and write went unnoticed until a reviewer wrote a five-line probe.
      Contract-first pays off only when something exercises the contract. This use case *is* the
      exercise, and it is the exact composition the bug lived in.
    - Composed as in ALT-8: the boolean guards the attempt, the sequence and source status ride on
      `LoadedCustomer`, the outcome selects the branch, the re-read decides the answer. It is
      `@Transactional`, per ALT-8's deliberate asymmetry with ALT-9b.

    ### 🔴 ALT-8's snippet contradicts ALT-8's own bullet — the prose wins
    The snippet routes three conditions into two exception types chosen for *where the code sits*
    rather than for *what an operator must do*. The bullet a few lines below it already says **"both
    corruption paths use `CustomerRowCorruptException` — same corruption must not produce two
    operational outcomes"**. The shipped code follows the bullet. **Do not copy the snippet into the
    ADR as written.**

    | Condition | ALT-8 snippet | Shipped | Why |
    |---|---|---|---|
    | `load` finds no row | `CustomerRowMissingException`, in the use case | **`CustomerNotFoundException`**, thrown by the **adapter** | The caller named a customer that was never registered — a client error, a 404. |
    | Superseded re-read finds no row | `CustomerRowMissingException`, in the use case | **`CustomerRowCorruptException`**, thrown by the **adapter** | The conditional write it follows has already established that the row exists, and v1 has no delete path. Reporting it as "not found" hands the operator a 404 for a database that just lost a row. |
    | Superseded re-read still `ONBOARDING` | `IllegalStateException("unreachable")` | **`CustomerRowCorruptException`**, in the use case | A transition consumed the sequence number without applying the status change, so the audit trail and the customer row disagree — the definition in the exception's own javadoc. And it is **reachable**: transition-implies-status-change is a convention, not a schema constraint (see the 🔴 in ALT-8). `IllegalStateException` falls to the catch-all — a 500 with **no corruption alert**. |

    The exhaustive switch on the re-read is kept exactly as ALT-8 argues — it is the tripwire the
    `ON CONFLICT` clause loses, so B-1's `SUSPENDED` fails compilation here.

    ### 🔴 Both `CustomerStore` reads dropped their `Optional` — the port changed, not just the caller
    `load` returns `LoadedCustomer` and `findStateAfterSupersededTransition` returns `CustomerState`;
    absence is the adapter's exception in both. **Every caller that has ever existed — one use case
    and six test sites, five unwrapping immediately and one asserting emptiness** — treated absence
    as exceptional, so the `Optional` modelled a case no caller had.
    - **The sibling BC settles the shape.** `AccountLoader.loadAccount` returns a non-optional
      `Account` across four call sites with **zero** `orElseThrow`; absence surfaces *below* the
      service, as `AccountHistoryNotFoundException` out of `Account.rehydrate`. And within this BC
      `CustomerStore.linkAdmission` already threw on an absent customer, so `load` was the odd one
      out twice over.
    - **The exception types stay in `application`** — "it is only thrown in the repository, so move
      it there" is the obvious next suggestion, and it is wrong. **🔴 [corrected 2026-08-13 — an
      earlier revision of this bullet claimed the placement was mechanically enforced *two*
      independent ways. One of the two does not exist, and the claim was checked with `javap` rather
      than argued.]**
      1. ~~`CustomerStore` declares both in `@throws`, so `..application..` would depend on
         `..infrastructure..`.~~ **False.** Both exceptions are unchecked, so the `@throws` is a
         *javadoc tag*, not a `throws` clause — the signatures carry none. Javadoc does not survive
         compilation: `javap -v` on the compiled `CustomerStore` shows **no constant-pool entry** for
         either type. ArchUnit reads bytecode, so no rule can see it. Moving the type into
         `..infrastructure.db.repository..` today would compile and pass every architecture test.
      2. **Real, but prospective.** Increment 5 item 5's `CustomerExceptionHandler` will live in
         `..infrastructure.web..` and reference the type as an `@ExceptionHandler` class literal — a
         genuine bytecode dependency — which `web_must_not_depend_on_other_io_siblings` forbids from
         reaching `..infrastructure.db..`. Account already demonstrates it:
         `TransactionIdConflictException` is thrown **only** in `ProcessedTransactionRepository` and
         still lives outside `infrastructure`, because `AccountExceptionHandler` maps it.

      **So today the placement is a convention with no build-time teeth, and it acquires them when
      the handler lands.** The standing rule this repository already applies to `GRANT`/`REVOKE`
      applies to architecture rules too: **a dependency that exists only in javadoc, an import or a
      comment is documentation wearing a constraint's clothes.** Check the constant pool before
      calling anything enforced.
    - **Where this BC diverges from account, deliberately:** account puts all 16 exception types in
      `domain/exception`. Customer splits them — domain invariants in `domain/exception`,
      **port-contract failures in `application`**, alongside `EmailAlreadyRegisteredException` and
      `CustomerNumberCollisionException`, which are also adapter-thrown and port-declared. "No row
      for this id" is not an invariant the aggregate can state, so it is not a domain exception.
      **This split, not the false enforcement claim above, is the actual justification.**
    - **Named `CustomerNotFoundException`, NOT `CustomerRowMissingException` as this item originally
      specified.** The rename is not cosmetic: the near-identical names invited exactly the handler
      the table above exists to prevent — one that treats the pair alike — when the whole point is
      that one is a client 404 and the other a 500 plus a corruption alert. `Row` also leaks a
      storage noun into a type increment 5 puts in the public error contract, beside two siblings
      that carry none. `CustomerRowCorruptException` keeps its `Row`: it genuinely is about a
      persisted row contradicting itself.

    ### Two follow-ups this item creates
    - **`ActivationResult` is a record with a boolean, while `RegistrationOutcome` beside it is a
      sealed interface. Decided, not drifted:** the two shapes stay, because B-1's `SUSPENDED` does
      not give activation a third outcome. Activating a suspended customer is `reinstate`, a
      different transition, so `Customer.activate`'s exhaustive switch is where `SUSPENDED` must be
      answered — the build breaks there and the boolean stays correct. **If that call is ever
      reversed, `ActivationResult` must become sealed in the same change**, or the web mapper is the
      one place in this BC with no exhaustiveness tripwire.
    - **The residual risk to READ COMMITTED is configuration, not code** — Hikari's
      `transaction-isolation`, or a per-role `default_transaction_isolation` in Postgres. Neither an
      annotation nor javadoc defends against those, which is why
      `@Transactional(isolation = READ_COMMITTED)` was **declined**: under `PROPAGATION_REQUIRED` an
      inner transaction's isolation attribute is silently dropped, so it would read as a guarantee on
      the one property this design depends on while providing none. The real control is the measured
      table in ALT-8: every other isolation level fails **loudly** rather than fabricating a
      corruption verdict.
    - **`RegisterCustomer` does NOT move.** It genuinely needs item 7's number generator and item
      14's `evaluate`, so it stays at increment 5 item 1.

## Increment 5 — application + web

1. `RegisterCustomer` + `CustomerApplicationService`, following **ALT-9b's three transaction
   boundaries exactly** — the rollback trap is invisible in a linear step list, so do not
   "simplify" it into one `@Transactional` method.
2. ⬆️ **MOVED to increment 4 item 15 (2026-08-11) — do not build it twice.** `ActivateCustomer` is
   not blocked by anything in increment 4: it needs only the ports item 5 landed, and mocked-port
   tests. It runs right after item 6, because until some caller exercises the port composition, the
   contract is asserted rather than demonstrated — which is precisely how item 5's 🔴 survived
   review. Rationale and scope live at item 15.
3. Temporal DoB validation (not-future / min age) — needs `Clock`. **Closes B-7.**
   *Why here and not in `IndividualDetails`:* both predicates are **time-varying**, so by the same
   argument that keeps admission out of `CountryCode`, putting them in the VO makes a row
   un-rehydratable the day the minimum age changes. Write that in the code comment, or a future
   contributor "fixes" it by injecting a `Clock` into the domain and the ArchUnit purity rule becomes
   the villain instead of the referee.
4. OpenAPI `src/main/resources/openapi/customer/customer-api.yaml`:
   ```
   POST /customers                  201  registerCustomer   (Idempotency-Key header, required)
   GET  /customers/{id}             200  getCustomerById
   POST /customers/{id}/activate    200  activateCustomer   → { status, activatedAt, alreadyActive }
   ```
   - Boundary constraints in the **YAML**, not hand-added annotations — the DTOs are generated.
     `maxItems: 10`, `uniqueItems: true` on nationalities.
   - **v1 emits nothing external.** No projector, no outbox, no Kafka. Welcome comms and integration
     events arrive with **Seam B**, through the outbox. Stated explicitly because increment 2's
     rationale mentions "integration event", and the obvious next move —
     `kafkaTemplate.send()` inside the `@Transactional` use case — is precisely the dual-write problem
     the account outbox+CDC pipeline exists to prevent.
   - `alreadyActive` comes from the **re-read status**; the rowcount only selects the branch.
   - **Refusal responses must be generic** (ALT-5 tipping-off). The response carries the outcome
     only — never any `AdmissionMatch` field, and never a message interpolating one. Enforced by
     item 9 (type channel) **and** the constant-message rule in ALT-5 (message channel), not by
     remembering.
   - **Deliberately absent:** `GET /customers` (needs pagination + authz) and
     `PUT /customers/{id}/email` (needs a confirmation flow).
5. Controllers + explicit DTO↔domain mappers (one class per direction, **sorting nationalities on the
   way out**) + `CustomerExceptionHandler`:
   - **Advice ordering is a real trap.** `ExceptionHandlerExceptionResolver` iterates advice beans in
     order and **returns on the first advice with any matching method** — it does not select the most
     specific handler across advices. `shared…GlobalExceptionHandler` declares
     `@ExceptionHandler(Exception.class)` with **no `@Order`**, and neither does
     `AccountExceptionHandler`; both sit at `LOWEST_PRECEDENCE`, so today's correct behaviour is
     component-scan discovery order, which differs between filesystem and jar packaging. Fix while
     here: `@Order(Ordered.LOWEST_PRECEDENCE)` on `GlobalExceptionHandler`, `@Order(0)` on each
     per-BC advice.
   - Mapping: domain exceptions → 422; **corruption → 500 + alert, third tier**, body must not carry
     `e.getMessage()`. **Two** existing handlers do exactly that and must not be copied:
     `GlobalExceptionHandler.handleException` and
     `AccountExceptionHandler.handleInternalServerErrorException` — the latter the likelier copy source.
   - **🔴 "Do not copy `GlobalExceptionHandler`" is not sufficient — FIX it, one line.** It is the
     `LOWEST_PRECEDENCE` catch-all, so it is what catches a jOOQ `DataAccessException` escaping the
     ALT-9b step-3 decision write — **the one write in the flow whose bind values are the sanctions
     match**. pgjdbc's `logServerErrorDetail` defaults to `true`, propagating the server `DETAIL`
     (including `Failing row contains (…)`) into the exception message, and jOOQ concatenates the
     cause. So a `CHECK` or `NOT NULL` violation on `admission_decision_match` has a live path to a
     500 body containing `SANCTIONED, NATIONALITY, RU`. Change the body to a constant and keep the
     detail in the log (it already logs the full stack). Optionally set `logServerErrorDetail=false`
     on the datasource as defence in depth. For a criminal-liability rule, leaving the fallback
     handler leaky while telling the *new* handler to behave is not proportionate.
   - **🔴 [added 2026-08-11, found reviewing item 6] The leak is not confined to
     `admission_decision_match`, and the bigger payload is `customer.customer`.** Verified against
     the local PG18: a `CHECK` violation on an `UPDATE` emits `Failing row contains (…)` exactly as
     an `INSERT` does, and the customer row carries **name, email, date of birth and residence in one
     line** — a fuller disclosure than any match row. Two constraints are reachable **without any
     admission involvement**, so the gate is the *registration and activation* flows, not the
     admission flow:
     - `ck_customer_date_of_birth_plausible` on the insert path. `V1` states outright that nothing
       bounds the date today, because the use case that would does not exist — so a 19th-century date
       of birth reaches Postgres, and `CustomerRepository` catches only `DuplicateKeyException`.
     - `ck_customer_activation_not_before_registration` on the activation path, under clock skew or a
       backdated activation. Nothing translates it.
     Both are unreachable from HTTP **only while the BC has no controller**, which is what increment 5
     adds. So this bullet is the gate on shipping increment 5, not a backlog entry — and it is the
     reason `logServerErrorDetail=false` is worth more than "optionally": it kills the `DETAIL` at the
     source for every table and every constraint, without waiting on handler coverage, and it does not
     disturb `PSQLException.getServerErrorMessage().getConstraint()`, which is a parsed field rather
     than rendered text.
   - **🔴 [added 2026-08-08] ADR-009 names TWO ship gates on this file, and this plan tracked only
     one.** The ADR's constraint reads: before the admission flow ships, the catch-all handler
     returns a constant body **"and the validation handler stops populating `rejectedValue`"**. That
     second half is live today — `GlobalExceptionHandler`'s `MethodArgumentNotValidException`
     handler copies `fieldError.getRejectedValue()` straight into the **400** body. A 400 echoing a
     submitted nationality, date of birth or email is the same PII/tipping-off class as the 500
     path, and it fires on ordinary bad input rather than on a rare constraint violation, so it is
     the *more* frequently exercised leak of the two.
     - **🔴 It is NOT a contract change, and the YAML must NOT be touched.** D7 is explicit:
       *"Ceasing to populate it is not a contract change … Only deleting it from the schema would be
       breaking, and that is not required."* `openapi/shared-api.yaml` declares `FieldError` with
       `required: [field, message]` and `rejectedValue` optional + nullable, so every conformant
       client already handles its absence. **The fix is deleting one `.rejectedValue(...)`
       call** in `GlobalExceptionHandler.handleMethodArgumentNotValidException` — **plus one test
       edit that is not optional**: `AccountControllerTest:128` asserts
       `jsonPath("$.errors[0].rejectedValue").value("-100.50")` and will go red. **Invert it to
       `.doesNotExist()`, do not delete it.** Deleting is the path of least resistance and leaves
       D7 channel 4 with **no** standing guard — note the asymmetry it would create: channel 1 gets
       a build-failure guard (the tag-key-set test, increment 5 item 6) on the argument that it is
       "the only testable channel", while channel 4 is *equally* testable and already has a test
       sitting on the exact JSON path. Inverting converts a one-off ship gate into a permanent
       control for free. Do not edit
       `shared-api.yaml` — it is a **cross-BC** contract and `FieldError` shapes every account 400
       body too, so deleting the field there *would* be the breaking change D7 rules out.
       *(ADR-009 is internally inconsistent here: its Negative-consequences section says flatly
       "Removing `rejectedValue` is a contract change". **D7 is the authoritative statement** — the
       Negative bullet is the loose paraphrase. Expect a future reader to hit it and re-raise this.)*
     - **Scope the gate honestly: removing the field closes the *body* channel only.** The same
       handler's `log.warn("Input validation exception: ", e)` still puts the rejected values in the
       logs via the exception message. The ADR discusses it inside channel 4's prose but treats it
       as the **log** channel (a review convention, not a constraint) and leaves it behind WP-57, so
       the ship gate is body-only by design. Do not record the log half as closed.
     - **⚠️ [added 2026-08-08 — NEEDS VERIFICATION, do not extract as settled] There may be a fifth
       400-body channel ADR-009 does not enumerate: `handleBadRequestException`.** It returns
       `e.getMessage()` in a 400 body for `HttpMessageNotReadableException` and
       `MethodArgumentTypeMismatchException`, and both message families **commonly embed the
       submitted value** (Jackson's `InvalidFormatException` renders `from String "…"`; Spring's
       type-conversion message renders the failed value). If that holds on this Boot version, a
       malformed `dateOfBirth` on `POST /customers` echoes the DoB — same PII class, same status
       code, same file as `rejectedValue`. This item currently names only `handleException` and
       `AccountExceptionHandler.handleInternalServerErrorException`. **Settle it with a two-minute
       `@WebMvcTest` before anyone claims the web-layer channels are enumerated.**
6. **Tests** — `customer.application` and `customer.infrastructure.web` are the packages added in
   this increment that produce mutants (increment 4 lands the ports in `application`, but interfaces
   generate none; `customer.domain` has been mutated since increment 2 and stays covered).
   **🔴 [corrected 2026-08-08] This item used to say `application` was the *only* one, "after
   increment 4 item 4 excludes `customer.infrastructure.*`". No such exclusion exists** — increment
   4 excluded one named class, not the package (see item 4 there), which is why item 7 below must
   exclude `customer.infrastructure.web.*` explicitly. Per `CLAUDE.md`, and split by what each kind
   of test can actually prove:
   - **`evaluate(subject, snapshot)` — pure unit tests, no mocks, no container** (ALT-5). ⬆️ **These
     ship with increment 4 item 14, not here** — the function is scheduled there and its tests come
     with it; this list stays as the specification of what they must cover.
     Construct the snapshot as a literal value. Evaluation order (a subject hitting both `SANCTIONED` and
     `UNLICENSED` records the **sanctions** refusal); deny-ANY vs allow-membership; a US-national
     FR-resident refused on `RESTRICTED_PERSON`/`NATIONALITY`; an FR-resident holding an
     unlicensed-market nationality **admitted**; a US-incorporated corporate refused on
     `INCORPORATION`; `RESIDENCE`-licensed-but-not-`INCORPORATION` admits the individual and refuses
     the corporate. **These are the real admission tests.**
   - **🔴 Do not write these as application-service tests with `CountryAdmissionPolicy` mocked** — a
     mocked port returns a configured `AdmissionDecision`, so the assertion proves only that the stub
     was configured. That signature is the tell that logic sat in the wrong layer, and it is exactly
     why ALT-5 moved evaluation out of the port.
   - **Application-service tests with mocked ports** for what genuinely is orchestration: the three
     transaction boundaries; the `alreadyActive` composition including the rowcount-0 re-read;
     idempotent replay returning the same body; **a refused registration still writing
     `admission_decision`**.
   - One unit test per mapper direction,
   `@WebMvcTest` for controllers — including one asserting a refusal body leaks **no** restriction,
   connecting factor or triggering country, **and one that the refusal exception's `getMessage()` is
   the constant** (the message channel the ArchUnit rule cannot see).
   - **🔴 [added 2026-08-08] A test pinning the admission counter's tag-key set to exactly
     `{restriction, connecting_factor, subject_type}`.** ADR-009 D7 splits the label rule into three
     channels of different strength and says **only this one is testable**: the tag keys are
     constructed in one place, so an assertion on the exact key set makes a new label — e.g. someone
     adding `triggering_country` — a **build failure** rather than a review catch. Without it, item
     8's label rule is a convention with no teeth, and it is the rule protecting PII-adjacent data
     from landing in an unbounded metric dimension.
7. **PITest**: `<excludedClasses>` += `…customer.api.generated.*`; `<excludedTestClasses>` +=
   `…customer.infrastructure.web.*`.
   - **🔴 [corrected 2026-08-08] `customer.infrastructure.web.*` DOES need adding to
     `<excludedClasses>` too.** This item used to say it was "subsumed by increment 4's
     `customer.infrastructure.*`". **There is no such wildcard** — increment 4 excluded the customer
     adapter *per class* (`…db.CustomerFlywayConfig`) precisely so the FPE generator stays mutated.
     Left uncorrected, the controllers and their generated-DTO plumbing enter the denominator and
     the 80% gate fails on a green feature.
8. **`POST /customers/{id}/activate` must not ship publicly before authz exists.** A compliance
   officer's action, not a customer's; unauthenticated it is a KYC bypass. The project has no Spring
   Security at all today — a project-wide gap, but this is the first endpoint where it is
   *exploitable rather than merely absent*. It is also the first caller needing an `actor` for
   `customer_status_transition`; until authz exists, `'SYSTEM'` is the honest placeholder.
9. **ArchUnit — tipping-off rule** (ALT-5): `RefusedDecision` may not be referenced from
   `..infrastructure.web..`. Lands here rather than in increment 3 because a rule over a package
   that does not exist yet passes **vacuously** — it would be green for a whole increment while
   proving nothing. Disclosing an AML/sanctions match is a criminal offence in most jurisdictions,
   so a compile-time guarantee is proportionate; the `@WebMvcTest` in item 6 is the behavioural
   half of the same guarantee.

---

## Backlog (unscheduled)

- **B-17 — 🔴 the ACCOUNT BC fails under method-order randomisation, and it is the same defect the
  customer standing rule exists to prevent.** Found 2026-08-14 while proving the customer hoist, and
  **measured on a clean tree at `9468951` so it is pre-existing, not caused by that change**:
  ```
  mvn test -Dsurefire.runOrder=random \
      -Djunit.jupiter.testmethod.order.default='org.junit.jupiter.api.MethodOrderer$Random'
  ```
  - `AccountEventRepositoryTest` — **4 errors**, `duplicate key value violates unique constraint
    "idx_event_store_event_id"` and a spurious `OptimisticLockingFailure`. Hardcoded event ids and
    versions shared across methods, exactly the literal-fixture problem customer just hoisted away.
  - `OutboxCleanupObserverTest.gauges_should_be_registered_at_construction_time` — passes alone,
    fails in the full suite, so this one is **cross-class shared state** (a `MeterRegistry` outliving
    a test) rather than method order. Different bug, same symptom; do not fix them as one.
  - **Why it is worth a ticket rather than a shrug:** these pass today only because JUnit's default
    method order is deterministic. Any JUnit upgrade, class rename or parallelisation flips them, and
    the failure will look like a flaky database rather than a fixture defect — which is precisely how
    the customer version of this cost half a day. The customer fix is the template:
    `CustomerFixtures` plus per-method minting.
  - **Do not "fix" it by pinning an order.** That hides the class of defect instead of removing it.

- **B-1 — KYC status axis.** Unblocks `suspend`/`reinstate`/`close` and the **transition** reason
  axis — distinct from ALT-5's admission `Restriction`/`ConnectingFactor`, which are settled; do not
  conflate the two when this lands. **When it
  lands:** (a) the exhaustive switch on the rowcount-0 re-read fails to compile — the intended prompt
  to decide what a suspended customer's activation returns; (b) `reason` on
  `customer_status_transition` becomes a `CHECK`-mandatory column for suspend/close; (c) ALT-1's
  table already exists, so no new audit table.
  **🔴 (d) [added 2026-08-06 by ADR-009] `close` has a hard, previously undeclared prerequisite: the
  purge path.** `CLOSED` is what starts the AML retention clock ("end of the business
  relationship"), and expiry carries a **mandatory** deletion duty. Sequence purge **before**
  `close`, not after. **But do not read the prerequisite as gated on B-1 alone** — the *refusal*
  clock (`admission_decision.decided_at`) starts unconditionally, so the real deadline is
  **whichever comes first: `CLOSED` shipping, or first production refusal + `cdd-years`**. The
  customer categories are compliant *by construction*; refusals are compliant *by calendar*.
  **(e)** B-1 also owns the **abandoned-onboarding** anchor — someone who registers and never
  activates has no retention anchor at all today, and is likely the highest-volume PII category in
  the schema. Needs `registered_at` + a stated abandonment window.
  **✅ (f) [settled 2026-08-10] `ONBOARDING` is the INITIAL state only — nothing may transition back
  into it.** Periodic KYC review, restriction and re-verification each get a **state of their own**;
  none of them is a return trip. Two consequences B-1 inherits rather than decides:
  - **It is already enforced.** `LoadedCustomer` rejects `ONBOARDING` with `sequenceNo >= 1` as
    corruption, so a design that reuses `ONBOARDING` as a review state fails at **read time**, on
    every load, for every affected customer. Change the invariant deliberately or design around it;
    do not discover it.
  - **Both `LoadedCustomer` guards are written per status, not as the biconditional
    `ACTIVE ⟺ sequenceNo >= 1`.** That equivalence is true only while there are two states — a
    SUSPENDED customer sits at `sequenceNo >= 2` and would trip it. The per-status form survives
    B-1 unchanged; **if you "simplify" it to the biconditional, you break it.**
- ~~**B-2 — PII, retention, erasure position**~~ — **position recorded 2026-08-06 in ADR-009**
  (increment 4 item 0). The label rule stands as written: counter tagged by
  `restriction`/`connecting_factor`/subject type, **`triggering_country` deliberately excluded**.
  The Seam B worry (c) is answered structurally — the published language is primitive-only lifecycle
  transitions, so no PII crosses the topic and its retention is not a data-protection control.
  **🔴 But read that as a gate, not as closed.** ADR-009 D1: *"'by construction' is an assertion
  until there is a schema review rule behind it. **Seam B must not ship without one.**"* The
  structural argument holds only while something enforces that the published language stays
  primitive-only; nothing does today.
  **What the ADR leaves open, and it is not small:** (i) **no purge path exists** — blocking for
  B-1, see increment 4 item 0; (ii) **erasure does not reach backups**, an accepted residual bounded
  by a backup retention this repo has not defined (feeds WP-117); (iii) the **log** half of the
  label rule is a review convention, not a constraint — only the metric-tag half is testable.
- ~~**B-3 — `CustomerIdTest`**~~ — **closed in `fccaa7e`**. `CustomerId.of` is only called from
  `static final` test field initialisers, which run once at class-init under the *unmutated* method,
  so its `NullReturnVals` mutant is unobservable. Real gap: the identical `AccountId`/`EventId`/
  `ReservationId` mutants are killed because account tests call `of` inside method bodies.
- **B-4 — `shared` is at 0% mutation coverage** (5/5 `MapperUtils` mutants `NO_COVERAGE`) — its only
  covering tests sit in `excludedTestClasses`. Hidden because `mutationThreshold` is a single global
  aggregate with no per-BC floor.
- **B-5 — Auto-discovering mutation gate.** Three verified facts about PITest's `Glob` first:
  1. `*` **does** span package separators (`*` → `.*`), so `org.girardsimon.wealthpay.*` matches
     `WealthpayApplication` — check it generates no mutants.
  2. **Brace alternation is not supported.** `Glob` escapes `.`, expands `*`, then calls
     `Pattern.compile`, where `{…}` is a repetition quantifier →
     `PatternSyntaxException: Illegal repetition near index 35`. Use the leading-`~` raw-regex escape
     (verified): `~org\.girardsimon\.wealthpay\..*\.(infrastructure|jooq|api\.generated)\..*`
  3. **`~` does not fully mean "hands off my regex".** `Glob`'s constructor runs
     `.replace("+", "\\+")` *after* stripping the `~`, so `+` quantifiers become literals.

  That regex does **not** cover `shared.config.*`, which is in `excludedClasses` today. It shortens
  the list, it does not eliminate it.
- **B-6 — Naming: `activatedAt` → `firstActivatedAt`?** Once SUSPENDED → ACTIVE exists, "first ever"
  vs "current" is a real product question. *Note:* with ALT-1's transition table "first ever" becomes
  derivable (`MIN(occurred_at) WHERE to_status='ACTIVE'`), which weakens the case for renaming —
  `activatedAt` can honestly mean "current".
- **B-7 — Temporal DoB validation** *(scheduled: increment 5 item 3)*.
- ~~**B-8 — `registeredAt`**~~ — **closed in `fccaa7e`**.
- **B-9 — Seam A** (`docs/context-map.md`): `CustomerLookup` port owned by `account.application`,
  implemented in `customer.infrastructure`. Unchanged by any decision above.
- **B-10 — Tax residence + place of birth.** The remaining FATCA/CRS gaps. Tax residence can be
  plural (unlike `countryOfResidence`); place of birth is a Model IGA Annex I indicium. Neither is v1.
- **B-11 — Corporate sanctions screening needs UBO data** *(raised by ALT-5/Q2)*. OFAC's **50 Percent
  Rule** blocks entities owned ≥50% **in aggregate**, directly *or indirectly*, by blocked persons,
  and **OFAC publishes no list of them** — so `country_of_incorporation` screening is necessary and
  nowhere near sufficient. Needs beneficial-ownership data the model has none of. *(The
  ownership-not-control carve-out is **OFAC-specific**. The EU's 2024 Best Practices guidance and UK
  OFSI both apply an ownership **or control** test — de facto dominant influence, or a designated
  person reasonably expected to be able to direct the entity's affairs. For a non-US firm that is the
  binding test, so the gap is **wider** than the OFAC framing suggests.)* Vendor-screening territory,
  same conclusion as ALT-4's "do not build a sanctions list".
- **B-12 — "US person" is broader than the three country limbs** *(raised by ALT-5/Q2)*. The seeded
  `RESTRICTED_PERSON` rows cover citizenship, residence and incorporation. **Green-card holders** are
  a status with no country field to hang off, and the Model IGA Annex I **indicia** (US place of
  birth, address, phone, standing payment instructions) are not covered. Both need model changes, not
  seed rows. Overlaps B-10. **Do not let this doc be cited as "US person handled".**
- **B-13 — Corporates: incorporation ≠ place of establishment.** The corporate analogue of the
  residence/nationality split Q2 just forced for individuals: some regimes key on the **place of
  central administration / principal place of business**, which can differ from the country of
  incorporation (the letterbox-company case). `CorporateDetails` models only incorporation. When it
  lands it is a **new connecting factor value**, not a new table — which is the ALT-5 design paying
  off.
  **🔴 State the real cost, so the extensibility promise is one someone can hold you to.** A reader
  arriving from ALT-5's US-triple argument ("three seeded rows and **zero lines of code**") will
  carry that over. A new *connecting factor* is not free: it is a new enum constant **plus two
  `CHECK`-constraint migrations** (`ck_restricted_country_factor_known`,
  `ck_licensed_country_factor_known`), **plus** a policy-version bump, **plus** an
  `expected-factors.*` change, **plus** a `permits` change on `AdmissionSubject` that breaks
  compilation at every evaluation site. That is cheap, bounded and compiler-guided — which is
  exactly what "extensible" should mean and is a genuinely good outcome — but it is **not** "one
  row, no code". Same honesty applies to the 2026-08-10 answer that a **third customer type** must
  not require a rewrite: sealed types deliver that by making the compiler enumerate every site that
  must change, not by requiring no changes.

- **B-14 — nothing verifies that committed jOOQ sources match the migrations, and this repo already
  has a case of the drift** *(raised in review of item 3)*. `c641985` exists **only** because
  regenerating revealed that `account/jooq/Routines.java` had been stale since `949dd72`: V18 added
  `dbz_publication_is_canonical()` and nobody regenerated. Nothing caught it — CI compiles the
  committed sources and never regenerates.
  **🔴 Staleness is the SAFE direction; a dirty local DB is the dangerous one.** A missing column
  fails at compile time. But a leftover table from an abandoned branch, a hand-applied `ALTER`, or a
  column renamed in psql generates classes that exist in **no migration**, CI compiles them happily,
  and it surfaces as `column does not exist` at runtime in an environment built only from
  migrations. Two options, increasing cost:
  (a) a test in `PerBoundedContextMigrationTest` (the only one migrating both BCs into one
  container) walking every generated table and asserting it resolves —
  `dsl.select(t.fields()).from(t).where(DSL.falseCondition()).fetch()`, ~20 lines. **Be honest in
  its javadoc that it would NOT have caught the V18 drift**, since routines are not tables;
  (b) the real control — a CI job applying migrations to a `postgres:18` service container,
  generating into a temp `<directory>`, and `diff -r` against `src/main/generated-jooq`.
  Feasibility is already proven: `AbstractContainerTest` runs stock `postgres:18` and
  `PerBoundedContextMigrationTest` applies all 19 account migrations there, so no custom image or
  pg_cron is needed.
  *Second-order argument for building it:* verifying item 3's output required a reviewer to
  hand-diff 11 generated tables and five function signatures against V1–V6. **Nobody will redo that
  on the next schema change.**
- **B-15 — `jooq-codegen-local` no longer enforces "local"** *(created deliberately by item 3)*. The
  hardcoded `jdbc:postgresql://localhost:5432/wealthpay` was a crude guardrail; `${env.DB_URL}` is
  the app's datasource and can point at a tunnel, a shared dev DB or staging — silently generating
  **committed** source from that catalog, with `DB_USER`/`DB_PASSWORD` attached. The trade was
  judged net-positive (one source of truth beats an accidental guard, and a wrong-database run
  surfaces as a visible diff in tracked files before it reaches anything), but the guard is owed:
  profile-scoped `maven-enforcer` `requireProperty` with
  `<regex>jdbc:postgresql://(localhost|127\.0\.0\.1)[:/].*</regex>`. Cheap; pairs with B-14.
- **B-16 — two generated-code ergonomics calls, deliberately deferred to their first consumer**
  *(both raised in review of item 3, both declined there as speculative with zero call sites)*.
  Increment 4 item 6 writes the first mapper and is where they get decided with real code in view:
  (a) **four types named `Customer`** — `customer.jooq.Customer` (schema),
  `customer.jooq.tables.Customer` (table), `customer.jooq.tables.pojos.Customer` (POJO) and
  `customer.domain.model.Customer` (aggregate). Note `Tables.CUSTOMER_` carries a **trailing
  underscore** because schema name == table name. The `Tables.CUSTOMER_` static import hides the
  table class, so the live collision is POJO ↔ aggregate, in every mapper signature. A
  `<strategy><matchers>` entry settles it; the cost of deciding later is a regeneration plus a
  rename across a handful of files.
  (b) **`flyway_schema_history` is generated in both BCs** — a table the application must never
  touch, whose shape Flyway owns, so a Flyway major upgrade yields a spurious regeneration diff.
  Excluding it is right for **both** schemas at once; fixing customer alone would create exactly the
  asymmetry this plan avoids elsewhere.

## Doc updates owed

- ~~`docs/context-map.md` — "skeleton aggregate, no behaviour yet"~~ — **de-staled in `175d2c3`**;
  it now describes `register`/`activate`/`reconstitute`. **It went stale again on 2026-08-08 in a
  new place:** the context table still classifies Customer as "**domain layer only**", which stopped
  being true when `e1f39da` added `customer.infrastructure.db`. One-line fix, and it is the same
  line that increment 4 item 12 makes final.
- **🔴 `CLAUDE.md` does not know the Customer BC exists — zero occurrences of the word.** It opens
  with "**Two** Spring Modulith modules under `org.girardsimon.wealthpay`" and lists only `account`
  and `shared`. Stale since `86a4d3a`, and it is the file every future session reads *first*, which
  makes it the highest-leverage staleness in the repo. **The module list and the "Two modules"
  sentence are still owed** — fold them into the single edit described two bullets down, not a
  drive-by.
  - ✅ **The `mvn -Pjooq-codegen-local` note was corrected in `cf54df2`** — it now states that
    migrations must ALREADY be applied (codegen introspects the live catalog, so anything unmigrated
    is silently absent rather than an error), that all three JDBC params come from `${env.*}`, and
    the clean-shell failure text. **That error string was verified by running it, not written from
    memory** — the plausible-looking "No suitable driver found" is *wrong*; jOOQ actually reports
    `Cannot execute query. No JDBC Connection configured`. Still owed there: say it generates
    **both** schemas.
- `CLAUDE.md` says `HexagonalArchitectureTest` has "16 rules"; there are **17** `@ArchTest` methods
  today (verified) — and this plan adds two more (`reconstitute` visibility, increment 4 item 11;
  tipping-off, increment 5 item 9) → **19**. Fix the count once, at the end of increment 5, rather
  than three times. Fold it into the same edit as the module list above.
- ~~`Customer.java:35-37` contradicts ALT-8~~ — **done in `fccaa7e`**. The comment now states that
  the boolean is an in-memory guard and the write's rowcount is the authority.
- **🔴 ADR — country existence vs. admission** *(scheduled: increment 4 item 13 — NOT the end of
  increment 5)*, plus the **restriction × connecting-factor** model, the allow-list/deny-list
  polarity argument, the FATCA-vs-Reg S split, and the tipping-off rule. The decision most likely to
  be
  silently undone by a contributor who "just wants to validate against the country table", or who
  "fixes" a false refusal by widening a connecting factor globally, or who collapses the two policy
  tables into one with a `polarity` column. Disambiguate *admission* (country) from *eligibility*
  (Seam A) while writing it. **Include the Q1 answer** — a future contributor will propose a
  `primary` nationality field, and the counter-argument (Master Nationality Rule; FATCA is a
  set-predicate) needs to be somewhere that survives this file.
- **ADR — state-stored + transition log.** Why Customer is not event-sourced, why the transition
  table is an audit log rather than an event stream, and that the activation exactly-once guarantee
  lives in the data-modifying CTE, not in the aggregate.
