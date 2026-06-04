# Wealthpay Context Map

This document is the authoritative description of how Wealthpay's bounded
contexts relate to one another, the strategic DDD patterns that govern those
relationships, and the dependency rules that keep the modular monolith
acyclic. It is a *reference*, not a decision log — the rationale for
individual decisions lives in `docs/adr/`.

**Implementation status.** As of this writing the only fully realised
context is `account`. The `customer` context is a skeleton aggregate
(`Customer` + `CustomerId`, no behaviour yet) and **none of the
Account↔Customer integration described below is wired**. This document is the
agreed *target* design plus the rules that constrain how it gets built. Each
seam is tagged with the increment that delivers it (`[v1]`, `[v2]`).

---

## Bounded contexts

| BC | Module | Status | Owns (ubiquitous language) |
|----|--------|--------|----------------------------|
| **Account** | `org.girardsimon.wealthpay.account` | implemented | monetary balance, reservations, account lifecycle (open / active / closed), money movement |
| **Customer** | `org.girardsimon.wealthpay.customer` | skeleton | customer identity, KYC / verification, customer-status lifecycle |
| **shared** | `org.girardsimon.wealthpay.shared` | implemented | cross-cutting *technical* concerns only (Clock, JSON, exception base, mappers) — never domain concepts |

Account is event-sourced (see ADR-001). Each context is a `CLOSED` Spring
Modulith module; `shared` is `OPEN`.

---

## The relationship at a glance

A **Customer owns one or more Accounts**. A customer exists before any
account — onboarding and KYC precede account opening — and the customer's
lifecycle **drives** the account's: an account cannot be opened for an
ineligible customer, and a suspended or closed customer should ultimately
have their accounts frozen or closed.

In strategic-DDD terms the **Customer context is upstream** (the authority)
and the **Account context is downstream** (the consumer).

> **Naming caveat.** Eric Evans' *Customer/Supplier* pattern uses the word
> "Customer" to mean the *downstream consumer*. Here the upstream context is
> literally named `Customer`. To avoid confusion: in the pattern's
> vocabulary, the **`Account` context plays the "Customer" (downstream)
> role** and the **`Customer` context plays the "Supplier" (upstream)
> role**. The rest of this document uses *upstream/downstream* to stay
> unambiguous.

---

## Strategic context map

The conceptual plane — what belongs on a context map. Arrow direction is the
*initiation* (who reaches out to whom at runtime).

```
┌─ CUSTOMER (BC) · UPSTREAM (U) ─────────────────────────────────────────────────
│ authority over:  identity · KYC · account-status lifecycle
│ exposes  Open Host Service + Published Language   (wire = primitives only):
│            · identity ....... UUID
│            · lookup result .. primitive DTO
│            · lifecycle ...... CustomerVerified | Suspended | Closed        [v2]
└────────────────────────────────────────────────────────────────────────────────
       ▲                                                                  │
       │  Seam A · v1                                        Seam B · v2   │
       │  downstream PULLS                                upstream PUSHES  │
       │  query: "is this customer eligible?"          event: "suspended"  │
       │  (via CustomerLookup port)                                        ▼
┌─ ACCOUNT (BC) · DOWNSTREAM (D) ────────────────────────────────────────────────
│ stores:  account.CustomerId   (own VO, wraps the UUID)
│ relationship to Customer  =  Published Language + ACL
│    Seam A ingress:  wrap  UUID → account.CustomerId            (web / application)
│    Seam B ingress:  ACL   CustomerSuspended → FreezeAccount cmd            [v2]
│                           (account anti-corruption layer)
└────────────────────────────────────────────────────────────────────────────────
```

Seam A points **up** because the downstream initiates the pull; Seam B points
**down** because the upstream initiates the push. That up/down split *is* the
pull-vs-push distinction.

---

## The two integration seams

### Seam A — ownership & eligibility `[v1]`

The Account aggregate records **who owns the account** and validates the
owner is eligible at open time.

- Ownership identity enters via the `OpenAccount` command as a raw UUID and
  is wrapped into `account.domain.model.CustomerId` — a value object owned by
  the Account context, distinct from `customer.domain.CustomerId`. The two
  contexts share the *identifier format* (UUID), never the *type*.
- Eligibility is checked at open time through a `CustomerLookup` port
  (detailed below). Whether that check is synchronous or backed by a local
  read model is the one open consistency decision; the default is the
  in-process synchronous lookup, given this is a single deployable today.

### Seam B — lifecycle propagation `[v2, deferred]`

When a customer is verified, suspended, or closed, the Account context must
react (e.g. freeze or close the customer's accounts).

- The Customer context raises domain events internally and publishes them as
  **integration events** (`CustomerVerified | Suspended | Closed`) using the
  transactional outbox + CDC pipeline already established for Account events
  (ADR-0003, ADR-004). The published schema is a versioned, primitive-only
  contract.
- The Account context consumes those integration events through an
  **anti-corruption layer**: it translates `CustomerSuspended` into an
  Account-native command (e.g. `FreezeAccount`), and never treats the foreign
  event as a native domain event.

**Seam B is intentionally not built yet.** No Customer outbox, no events, no
listener exist. They land together when a real consumer materialises;
building the publish side before there is a consumer is speculative
infrastructure. The anti-corruption layer in the Account context is the
documented target *shape*, not current code.

---

## Why "Published Language + ACL", not "Conformist"

It is tempting to call Seam A *Conformist* — "Account just conforms to
whatever ID format Customer uses." That is the wrong term, and the
distinction is not pedantic: Conformist is a far weaker boundary than what
this design has.

*Conformist* means the downstream adopts the upstream's **model wholesale** —
same types, same vocabulary, no translation — and accepts being coupled to
upstream change. This design does the opposite: Account has its **own**
`CustomerId` type and translates the UUID at ingress.

The litmus test that keeps the term honest:

> **Would a change to Customer's identity model reach Account's domain?**

Under this design, if Customer replaced UUIDs with a structured
customer-number, only the ingress translation would change —
`account.CustomerId` and every aggregate method would be untouched. Under a
true Conformist relationship, that change would ripple straight into
Account's domain. Different blast radius, different name.

What we actually have is the upstream offering a **Published Language** (UUID
identity, primitive-typed lookup results, versioned integration events) and
the downstream protecting itself with an **Anti-Corruption Layer**. The
format of an identifier is not a model, so there is nothing to "conform" to.

---

## Compile-time dependency direction

The strategic map shows the *conceptual* direction (Account asks Customer).
The **compile-time** dependency points the other way, by design:

```
   account.*                ──▶  shared.*                     (existing rule, intact)
   customer.*               ──▶  shared.*                     (new BC, same rule)
   customer.infrastructure  ──▶  account :: customer-lookup   (DIP; infra-only, narrow)

   ✗  account.*  ──▶  customer.*          Account still depends on `shared` alone.
```

This is dependency inversion applied at the context boundary: the **consumer
owns the contract**, the **provider is the plugin**. The consequence worth
protecting is that `account` keeps depending on `shared` and nothing else —
its module rule is unchanged even though it gains a synchronous cross-context
interaction.

---

## Seam A wiring detail

```
   conceptual / runtime :  account  ──asks──▶  customer
   compile-time         :  customer.infrastructure ──implements──▶
                                      account.application.CustomerLookup     (DIP)

   port     CustomerLookup        @ account.application      ← consumer owns the contract
            returns UUID / primitive DTO   (never account.CustomerId)
   adapter  CustomerLookupAdapter  @ customer.infrastructure  ← provider is the plugin
   exposed  @NamedInterface("customer-lookup")  over the port package only
            (not account::application wholesale, or Customer reaches use-case services)
```

Two details are load-bearing:

1. **The port returns primitives** (a UUID and status flags), never
   `account.CustomerId`. This pins the compile edge to `account.application`
   and keeps it out of `account.domain` — no context ever depends on
   another's domain types.
2. **The exposed surface is a narrow named interface**, covering only the
   port's package (e.g. `account.application.customer`), not the whole
   application layer. Otherwise the Customer context could reach Account's
   use-case services through the same door.

---

## Module dependency rules (Spring Modulith)

```
account/package-info.java
    @ApplicationModule(type = CLOSED, allowedDependencies = { "shared" })

customer/package-info.java
    @ApplicationModule(type = CLOSED,
                       allowedDependencies = { "shared", "account::customer-lookup" })
```

- The **only** static cross-context edge is `customer → account::customer-lookup`.
- The reverse direction (`Customer → Account`, Seam B) is realised through
  **integration events only** — asynchronous, no compile-time import. This is
  what keeps the module graph acyclic. A *synchronous* call from Customer back
  into Account would create a cycle and is forbidden; if Customer needs
  account facts (e.g. "block offboarding while accounts are open"), it
  maintains a local read model fed by Account integration events.
- These rules are enforced by `ApplicationModules.verify()` (Spring Modulith)
  and the hexagonal ArchUnit suite — boundary violations fail the build.

---

## v1 scope and its operational limitation

`[v1]` delivers ownership recording and open-time eligibility (Seam A). It
does **not** deliver lifecycle propagation (Seam B).

> **Known, accepted limitation.** Until Seam B is wired, suspending or
> closing a Customer has **no automatic effect on existing Accounts**. A
> suspended customer's accounts remain fully operational — credit, debit, and
> reservation all continue to succeed, because the Account context has no
> signal. This is a deliberate, time-boxed gap, not an oversight; any control
> that depends on "suspended customer ⇒ frozen account" must not assume it
> exists before Seam B ships. The trigger that closes the gap is Seam B
> wiring.

This is called out here because it is a compliance-relevant fact, not a
backlog item — it must be a conscious risk acceptance.

---

## Assumptions — when this map no longer holds

- **Customer must grow real invariants.** The whole upstream/downstream
  relationship presupposes that `Customer` becomes a genuine aggregate with a
  verification/suspension state machine and offboarding rules. If `Customer`
  stays a profile/CRUD table with no invariants, it does not warrant a
  hexagon and this map collapses to "Account stores a `CustomerId`" — revisit
  before investing in the seams.
- **Ownership cardinality.** This map assumes a single owner per account. If
  joint accounts (multiple owners) are introduced, the ownership reference in
  the aggregate changes shape and Seam A must be reconsidered.
- **Number of lookup consumers.** The provider-implements-consumer-port (DIP)
  shape is ideal for a **single** consumer. When a second context needs
  customer lookups, `customer.infrastructure` would have to implement a port
  per consumer and become a hub depending on every downstream's application
  layer (inverting the stability gradient). That is the inflection point to
  switch to a classic **Open Host Service**: Customer publishes one
  `customer.api`, and each consumer protects itself with its own ACL.

---

## Related decisions

- ADR-001 — Event Sourcing for Account Aggregate (the Account context's model)
- ADR-0003 — Transactional Outbox Pattern with CDC (the Seam B publish pipeline)
- ADR-004 — Event Contract (the published-language schema discipline for Seam B)
