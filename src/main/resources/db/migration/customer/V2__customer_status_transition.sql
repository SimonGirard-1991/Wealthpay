-- V2: The KYC status-transition log.
--
-- This table does two jobs, and folding them together is deliberate. It is the audit trail that
-- state storage does not give for free -- who changed a customer's status, when, and why, retained
-- for the configured CDD period (customer.retention.cdd-years) -- and it is the optimistic
-- concurrency control for status changes, because sequence_no IS the aggregate version.
--
-- One generic append-only table, not a bespoke one per transition: there are two states today and
-- more coming, and a table per transition multiplies with them.
--
-- It is NOT an event stream. customer.customer remains the source of truth and state is never
-- rebuilt from these rows. The shapes are similar enough that someone will eventually offer to
-- "finish the job" and make this an event store; the reason not to is that a two-state aggregate
-- does not need one, and the audit obligation is satisfied without it.
--
-- Concurrency, and why the guard lives here rather than in a conditional UPDATE on customer:
-- under READ COMMITTED two concurrent activations both read ONBOARDING and both decide to write.
-- Only the status converges -- activated_at becomes last-write-wins, which jitters the
-- periodic-review anniversary, and both callers are told they performed the activation. Inserting
-- the audit row and applying the state change in a single data-modifying-CTE statement makes the
-- audit write and the guard the same operation, so that path cannot change status without leaving a
-- record.
--
-- SCOPE THAT CORRECTLY: it is a property of that ONE statement, not of the schema. Nothing here stops a
-- direct `UPDATE customer.customer SET status = ...` from committing with no transition row, or from
-- walking the status backwards -- verified, both succeed. Audit completeness for this table therefore
-- rests on every status change going through the CTE, which is a convention rather than a constraint.
--
-- ACCEPTED RESIDUAL, recorded because the audit trail is this table's whole purpose: closing it needs a
-- BEFORE UPDATE trigger on customer.customer permitting a status change only when a matching transition
-- row is written in the same transaction. Deliberately not built yet -- the mutating paths do not exist
-- -- and it is the same gap V6 records for the identity-bearing columns, of which status is the one that
-- matters most here.

CREATE TABLE customer.customer_status_transition
(
    customer_id UUID        NOT NULL,
    -- 1-based per customer, and the aggregate version. Note the deliberate inconsistency with
    -- admission_decision_match.ordinal, which is 0-based: this one is a version, that one is a list
    -- index. Anyone writing investigation SQL across both needs to know.
    -- Supplied by the caller from the value it read with the aggregate, never computed here as
    -- MAX(sequence_no) + 1: each statement takes a fresh READ COMMITTED snapshot, so deriving it
    -- would re-open the lost update and additionally fabricate a transition row whose from_status
    -- was never checked against anything.
    sequence_no INTEGER     NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    to_status   VARCHAR(20) NOT NULL,
    -- Both timestamps are required. occurred_at is caller-supplied, so on its own backdating is
    -- undetectable; a regulator reading this log needs the claimed time and the observed one.
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 'SYSTEM', or the subject identifier of the compliance officer who acted. There is no
    -- authorization in the application yet, so 'SYSTEM' is the honest value until there is.
    actor       TEXT        NOT NULL,
    -- Null for transitions that need no justification. Becomes mandatory, via a CHECK added with
    -- the migration that introduces them, for suspension and closure.
    reason      TEXT,

    CONSTRAINT pk_customer_status_transition PRIMARY KEY (customer_id, sequence_no),

    -- RESTRICT, not CASCADE: this row is evidence about the customer and must outlive the customer
    -- row. ADR-009 D5 records that CASCADE would silently defeat the append-only triggers below,
    -- because PostgreSQL runs referential actions as the table owner rather than as the caller.
    -- Known hole, recorded rather than papered over: an ONBOARDING customer has zero transition
    -- rows, so RESTRICT never engages for them.
    CONSTRAINT fk_customer_status_transition_customer
        FOREIGN KEY (customer_id) REFERENCES customer.customer (id) ON DELETE RESTRICT,

    CONSTRAINT ck_customer_status_transition_sequence_positive CHECK (sequence_no >= 1),
    CONSTRAINT ck_customer_status_transition_from_known CHECK (from_status IN ('ONBOARDING', 'ACTIVE')),
    CONSTRAINT ck_customer_status_transition_to_known CHECK (to_status IN ('ONBOARDING', 'ACTIVE')),
    -- A transition to the status already held is not a transition. This does not catch the
    -- fabricated-row case above, which needs the previous row's to_status and so is not expressible
    -- as a CHECK; it catches the cheaper mistake of passing the same value twice.
    CONSTRAINT ck_customer_status_transition_changes_status CHECK (from_status <> to_status),
    -- The chain as a whole is not expressible, but its base case is: a customer's first transition
    -- can only leave ONBOARDING, because that is the only status register() produces. This is the one
    -- end of the fabricated-row problem a single-row constraint can reach.
    CONSTRAINT ck_customer_status_transition_starts_from_onboarding
        CHECK (sequence_no > 1 OR from_status = 'ONBOARDING'),
    CONSTRAINT ck_customer_status_transition_actor_present CHECK (actor <> '')
);
