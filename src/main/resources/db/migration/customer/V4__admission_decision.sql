-- V4: The admission audit record -- why we admitted or refused an applicant, and against which
-- policy version.
--
-- This is the table the compliance design depends on. A refused registration must still leave a
-- durable record: years later the firm has to be able to evidence why it declined someone, and in
-- several regimes the refusal itself is reportable. That is why the decision is written in its own
-- transaction, committed before the refusal is thrown, rather than inside the registration
-- transaction that a refusal rolls back.
--
-- It is also where the specifics of a refusal are allowed to live, and the only place. Telling an
-- applicant that they matched a sanctions or AML check is a criminal offence in most jurisdictions,
-- so the client-facing response carries the outcome and nothing else.

CREATE TABLE customer.admission_decision
(
    id              UUID        NOT NULL,

    -- Indexed but NOT unique, deliberately. A refused applicant may correct their details and retry
    -- with the same idempotency key, and each attempt is its own audit record. This is also the only
    -- correlation handle to the registration, since there is no customer_id column -- see below.
    idempotency_key TEXT        NOT NULL,

    -- Read in the same statement as the rules that were applied, so this cannot name a policy that
    -- was never the one evaluated.
    policy_version  BIGINT      NOT NULL,

    outcome         VARCHAR(10) NOT NULL,
    -- Not redundant with the evaluated inputs: the refusal metric is partitioned by subject type,
    -- and a licensing outage that refuses every corporate while individuals keep succeeding is
    -- investigated from this table.
    subject_type    VARCHAR(10) NOT NULL,

    -- The evaluated inputs, frozen. An array rather than a child table because these are never
    -- joined or searched by element -- the FATCA report reads customer_nationality, not decisions --
    -- and a snapshot that is only ever read whole does not earn a table.
    -- The recorder sorts before writing, because set iteration order is randomised per JVM run, so two
    -- identical retries would otherwise record different arrays for the same evaluation in a table a
    -- human reads during an investigation.
    -- Read that as a writer convention, not a property of the stored rows: neither sortedness nor
    -- distinctness is enforced below. Both need a subquery over the array's elements, which a CHECK
    -- cannot have, and neither can produce a wrong decision -- only a confusing audit record. The
    -- constraint below covers what does matter: presence, count, nullability and shape.
    nationalities            VARCHAR(2)[],
    country_of_residence     VARCHAR(2),
    country_of_incorporation VARCHAR(2),

    -- Both timestamps, for the same reason as customer_status_transition: a caller-supplied time
    -- alone makes backdating undetectable.
    -- decided_at is also the statutory retention anchor for a refused applicant, who has no customer
    -- row to inherit one from (ADR-009 D3), as well as what the "how many sanctions refusals last
    -- quarter" question reads.
    decided_at      TIMESTAMPTZ NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_admission_decision PRIMARY KEY (id),

    -- The composite target customer_admission's foreign key needs. Redundant as an index given the
    -- primary key, and kept because PostgreSQL requires a unique constraint on exactly the referenced
    -- columns -- the same trade uq_customer_id_kind makes on customer.
    CONSTRAINT uq_admission_decision_outcome_subject UNIQUE (id, outcome, subject_type),

    CONSTRAINT ck_admission_decision_outcome_known CHECK (outcome IN ('ADMITTED', 'REFUSED')),
    CONSTRAINT ck_admission_decision_subject_type_known CHECK (subject_type IN ('INDIVIDUAL', 'CORPORATE')),
    CONSTRAINT ck_admission_decision_policy_version_positive CHECK (policy_version > 0),

    -- array_position is the load-bearing half: array_to_string SKIPS null elements, so the regex
    -- alone accepts ARRAY['FR', NULL] by rendering it as 'FR'. Verified against PostgreSQL 18. Same
    -- family of trap as a nullable column defeating a CHECK. The upper bound mirrors the Nationalities
    -- value object, which is where the number is explained.
    CONSTRAINT ck_admission_decision_nationalities_shape CHECK (
        nationalities IS NULL
            OR (array_length(nationalities, 1) BETWEEN 1 AND 10
                AND array_position(nationalities, NULL) IS NULL
                AND array_to_string(nationalities, ',') ~ '^[A-Z]{2}(,[A-Z]{2})*$')
        ),
    CONSTRAINT ck_admission_decision_residence_shape
        CHECK (country_of_residence IS NULL OR country_of_residence ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_admission_decision_incorporation_shape
        CHECK (country_of_incorporation IS NULL OR country_of_incorporation ~ '^[A-Z]{2}$'),

    -- The evaluated inputs must match the subject type, or the record does not describe an
    -- evaluation that could have happened. ELSE FALSE for the same reason as on customer.
    CONSTRAINT ck_admission_decision_subject_columns CHECK (
        CASE subject_type
            WHEN 'INDIVIDUAL' THEN nationalities IS NOT NULL
                AND country_of_residence IS NOT NULL
                AND country_of_incorporation IS NULL
            WHEN 'CORPORATE' THEN country_of_incorporation IS NOT NULL
                AND nationalities IS NULL
                AND country_of_residence IS NULL
            ELSE FALSE
            END
        )

    -- NO customer_id COLUMN, AT ALL -- not even nullable.
    --
    -- No customer row exists when this is written, in any scenario: the decision is committed before
    -- registration, so that a refusal cannot roll it back. A nullable column would therefore be NULL
    -- on every path, which is worse than an absent one -- an investigator joining decision to
    -- customer reads NULL as "this was a refusal" and gets it wrong for every admitted row.
    -- Correlate on idempotency_key, which is stable across both outcomes and across retries. An
    -- admitted decision additionally acquires a customer_admission row below.

    -- NO DENORMALISED PRIMARY MATCH either. Copying the head match onto this row looks like the kind
    -- discriminant on customer, and does not transfer. The invariant actually wanted is
    -- "outcome = 'REFUSED' if and only if at least one match row exists", which is a cardinality rule
    -- across two tables; PostgreSQL forbids subqueries in CHECK, so denormalising would buy only an
    -- intra-row check on a copy, plus a trigger to keep the copy honest. Both benefits it was proposed
    -- for are cheaper without it: the period count is the partial index below, and reading one
    -- decision's head match is a one-row join on a primary key.
    -- The cardinality rule itself IS enforced, by the deferred constraint trigger at the foot of this
    -- file -- it just needs no duplicated column to do it.
);

CREATE INDEX idx_admission_decision_idempotency_key
    ON customer.admission_decision (idempotency_key);

-- For the retention purge, which scans by the anchor rather than by applicant, and for the
-- "refusals in a period" question, which the partial index below cannot answer alone because it
-- needs this timestamp through the join.
CREATE INDEX idx_admission_decision_decided_at
    ON customer.admission_decision (decided_at);

-- Every match, not just the most serious one. The deny quantifier is "any hit refuses", so several
-- rules fire routinely: a dual FR/RU national resident in IR matches sanctions twice, on
-- RU/NATIONALITY and IR/RESIDENCE, and keeping one would silently discard the other.
CREATE TABLE customer.admission_decision_match
(
    decision_id        UUID        NOT NULL,
    -- Encodes the evaluation's total order -- most serious first -- so stored rows replay in the
    -- order they were produced. ordinal 0 is the primary match.
    -- 0-based, unlike customer_status_transition.sequence_no, which is 1-based: this is a list index,
    -- that is an aggregate version. Never read these rows without ORDER BY ordinal.
    ordinal            INTEGER     NOT NULL,
    restriction        VARCHAR(20) NOT NULL,
    connecting_factor  VARCHAR(20) NOT NULL,
    triggering_country VARCHAR(2)  NOT NULL,

    CONSTRAINT pk_admission_decision_match PRIMARY KEY (decision_id, ordinal),

    -- RESTRICT, like every audit foreign key here (ADR-009 D5).
    CONSTRAINT fk_admission_decision_match_decision
        FOREIGN KEY (decision_id) REFERENCES customer.admission_decision (id) ON DELETE RESTRICT,

    CONSTRAINT ck_admission_decision_match_ordinal_non_negative CHECK (ordinal >= 0),

    -- UNLICENSED IS admissible here, unlike in restricted_country, and the asymmetry is not an
    -- oversight: this table records why a subject was refused, and being in an unlicensed market is
    -- one of the reasons. restricted_country holds deny-list rules only, and UNLICENSED is produced
    -- by the allow-list.
    CONSTRAINT ck_admission_decision_match_restriction_known
        CHECK (restriction IN ('SANCTIONED', 'RESTRICTED_PERSON', 'UNLICENSED')),
    CONSTRAINT ck_admission_decision_match_factor_known
        CHECK (connecting_factor IN ('NATIONALITY', 'RESIDENCE', 'INCORPORATION')),
    -- No foreign key to any country list, same reason as on the policy tables.
    CONSTRAINT ck_admission_decision_match_country_shape CHECK (triggering_country ~ '^[A-Z]{2}$')
);

-- Answers "how many sanctions refusals in a period", counting one row per decision rather than one
-- per match, which is what makes the ordinal = 0 predicate part of the index rather than a filter.
CREATE INDEX idx_admission_decision_match_primary_restriction
    ON customer.admission_decision_match (restriction)
    WHERE ordinal = 0;

-- The retention anchor for an ADMITTED decision (ADR-009 D6).
--
-- A refused decision stands on its own: it has its evaluated inputs and its own decided_at. An
-- admitted one does not -- it is the onboarding screening evidence for a real customer, so it is
-- retained until the relationship ends plus the configured CDD period, not until the decision date
-- plus that period. Its only structural path to the customer ran through processed_registrations,
-- which is prunable operational data, so pruning that table would strand the evidence with no
-- computable anchor. This link is written inside the transaction that creates the customer, where a
-- customer row exists, so it reintroduces none of the phantom-reference problem that dropping
-- customer_id solved.
--
-- 🔴 "A refusal never acquires a link, so the two outcomes stay structurally distinguishable" is the
-- claim, and it is ENFORCED below rather than asserted. An earlier version of this table carried two
-- independent foreign keys and nothing else, which made the sentence a write-path convention: a REFUSED
-- decision could be linked to a customer, and a CORPORATE decision to an individual. Both states are
-- audit-integrity defects rather than cosmetic ones -- a linked refusal reads as an onboarded customer
-- to anyone joining these tables, and it silently moves that record from the decided_at retention clock
-- onto the relationship-end one.
--
-- Enforced declaratively, by the same denormalise-and-use-a-composite-foreign-key technique that keeps
-- customer_nationality individuals-only, rather than by another trigger:
--   * outcome is carried here, pinned to 'ADMITTED' by a CHECK, and the composite key makes it agree
--     with the decision it names;
--   * subject_type is carried here and keyed against BOTH the decision's subject_type and the
--     customer's kind, so the two must be the same value.
-- Both columns are NOT NULL, which is what makes either key work at all: the default MATCH SIMPLE skips
-- a composite foreign key entirely when any referencing column is NULL, and a CHECK passes on NULL.
--
-- WHAT IS STILL NOT ENFORCED, stated because the list above is thorough enough to be over-read: an
-- admitted CORPORATE decision links cleanly to ANY corporate customer, not only to the applicant it was
-- evaluated for. Closing that needs an identity handle the two rows share, and the only candidate --
-- idempotency_key -- lives on the decision and in prunable processed_registrations, never on the
-- customer. So it is a genuine residual rather than an oversight, and the write path is what keeps the
-- pairing honest.
CREATE TABLE customer.customer_admission
(
    customer_id  UUID        NOT NULL,
    decision_id  UUID        NOT NULL,
    -- Denormalised from admission_decision purely so the keys below can constrain them.
    -- 🔴 NEVER READ THESE -- join to admission_decision instead. They are a second source of truth, and
    -- the one bypass this schema already accepts makes them lie: under session_replication_role =
    -- replica the foreign keys are skipped, and a link committed that way can read ADMITTED/CORPORATE
    -- here while the decision says REFUSED and the customer is an individual. Under the plain foreign
    -- keys it replaced, that state was at least visible to anything that joined; now the link row reads
    -- clean to anything that trusts these columns.
    outcome      VARCHAR(10) NOT NULL,
    subject_type VARCHAR(10) NOT NULL,
    linked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Composite, because one customer may legitimately hold more than one decision: two concurrent
    -- requests sharing an idempotency key each commit their own decision row, and the replay branch
    -- links the second one too rather than leaving admitted evidence unanchored. So this primary key
    -- carries an index, not the cardinality rule -- it covers the purge job's customer-to-decisions
    -- traversal, which is the direction retention is computed in.
    CONSTRAINT pk_customer_admission PRIMARY KEY (customer_id, decision_id),
    -- THIS is the cardinality rule: a decision anchors to at most one customer.
    CONSTRAINT uq_customer_admission_decision UNIQUE (decision_id),

    -- Only an admitted decision is CDD evidence for a customer, so only an admitted decision may be
    -- anchored to one.
    CONSTRAINT ck_customer_admission_admitted_only CHECK (outcome = 'ADMITTED'),
    CONSTRAINT ck_customer_admission_subject_type_known
        CHECK (subject_type IN ('INDIVIDUAL', 'CORPORATE')),

    -- Both RESTRICT. A CASCADE on customer_id would destroy the anchor while the evidence it dates
    -- survives, which is this table's own failure mode arriving from the other end.
    -- Each composite key subsumes the plain single-column one it replaces; only one is declared.
    CONSTRAINT fk_customer_admission_customer
        FOREIGN KEY (customer_id, subject_type) REFERENCES customer.customer (id, kind)
            ON DELETE RESTRICT,
    CONSTRAINT fk_customer_admission_decision
        FOREIGN KEY (decision_id, outcome, subject_type)
            REFERENCES customer.admission_decision (id, outcome, subject_type) ON DELETE RESTRICT
    -- Residual, uniform with every other foreign key here: referential integrity is enforced by
    -- internal triggers, which `session_replication_role = replica` skips. The declared triggers in V6
    -- are ENABLE ALWAYS and survive that; these keys do not. Recorded rather than worked around,
    -- because making it a trigger would trade a declarative constraint for plpgsql to close a hole that
    -- every foreign key in this schema shares.
);

-- The outcome and its match rows must agree. This is a cardinality rule across two tables, so it is
-- not a CHECK -- but it is a deferred constraint trigger, evaluated at COMMIT, by which point the
-- decision and its matches have both been written.
--
-- Three states it makes unrepresentable, in order of seriousness:
--
--   1. ADMITTED WITH MATCH ROWS. "We found a sanctions hit and onboarded them anyway" is the worst
--      state this schema can hold, and nothing else here prevents it.
--   2. REFUSED WITH NO MATCH ROWS. A refusal with no recorded reason, in the one table whose whole
--      purpose is holding that reason for the configured retention period. Under the tipping-off rule
--      the client response is deliberately uninformative, so this table is the only record there is.
--   3. ORDINALS NOT CONTIGUOUS FROM ZERO. idx_admission_decision_match_primary_restriction indexes
--      WHERE ordinal = 0, so a set of matches starting at 1 silently drops out of the "how many
--      sanctions refusals in this period" answer -- returning a confidently wrong number, which is
--      worse than an error. Contiguity is checked as max(ordinal) = count - 1, which the primary key's
--      uniqueness turns into "exactly 0..n-1".
--
-- "Enforced in the single insert path" was the previous answer, and it holds only while there is one
-- insert path. This is five-year evidence in a regulated flow, so the guarantee belongs where it
-- cannot be routed around.
CREATE FUNCTION customer.assert_decision_matches_outcome()
    RETURNS trigger
    LANGUAGE plpgsql
AS
$$
DECLARE
    subject_id   UUID := (to_jsonb(NEW) ->> TG_ARGV[0])::UUID;
    decision     customer.admission_decision;
    match_count  INTEGER;
    top_ordinal  INTEGER;
BEGIN
    -- A misnamed TG_ARGV column would yield NULL, the lookup below would find nothing, and the guard
    -- would silently be off behind a green migration. Fail loudly instead.
    IF subject_id IS NULL THEN
        RAISE EXCEPTION 'trigger % is misconfigured: no column named %', TG_NAME, TG_ARGV[0];
    END IF;

    SELECT * INTO decision
      FROM customer.admission_decision
     WHERE id = subject_id;

    -- Unreachable through the foreign key, but a decision is never deleted, so a missing one means
    -- the row was rolled back rather than that the rule was broken.
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT count(*), max(ordinal) INTO match_count, top_ordinal
      FROM customer.admission_decision_match
     WHERE decision_id = subject_id;

    IF decision.outcome = 'REFUSED' AND match_count = 0 THEN
        RAISE EXCEPTION 'admission decision % is REFUSED but records no match', subject_id
        USING HINT = 'A refusal must record every rule that fired; it is the only record of the reason.';
    END IF;

    IF decision.outcome = 'ADMITTED' AND match_count > 0 THEN
        RAISE EXCEPTION 'admission decision % is ADMITTED but records % match(es)',
            subject_id, match_count
        USING HINT = 'A subject that matched a restriction must not be admitted.';
    END IF;

    IF match_count > 0 AND top_ordinal <> match_count - 1 THEN
        RAISE EXCEPTION
            'admission decision % has % match(es) but its highest ordinal is %; ordinals must be'
            ' contiguous from 0', subject_id, match_count, top_ordinal
        USING HINT = 'Ordinal 0 is the primary match and the partial index depends on it existing.';
    END IF;

    RETURN NULL;
END;
$$;

-- Attached to both sides: inserting the decision alone must fail for a refusal, and inserting matches
-- must fail against an admitted decision. Neither table permits UPDATE or DELETE -- the append-only
-- triggers block them -- so INSERT is the only event that can reach either state.
CREATE CONSTRAINT TRIGGER trg_admission_decision_matches_outcome
    AFTER INSERT
    ON customer.admission_decision
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION customer.assert_decision_matches_outcome('id');

CREATE CONSTRAINT TRIGGER trg_admission_decision_matches_outcome
    AFTER INSERT
    ON customer.admission_decision_match
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION customer.assert_decision_matches_outcome('decision_id');

-- ENABLE ALWAYS, and here it is the highest-value instance of that setting in the schema. At the default
-- enable state one `SET session_replication_role = replica` -- a legitimate mode used by
-- logical-replication apply workers and by restore and backfill tooling -- lets an ADMITTED decision
-- commit alongside a SANCTIONED match, which is the state this file calls the worst one it can hold.
-- ENABLE ALWAYS is accepted on a CONSTRAINT TRIGGER and leaves DEFERRABLE INITIALLY DEFERRED intact.
ALTER TABLE customer.admission_decision
    ENABLE ALWAYS TRIGGER trg_admission_decision_matches_outcome;

ALTER TABLE customer.admission_decision_match
    ENABLE ALWAYS TRIGGER trg_admission_decision_matches_outcome;
