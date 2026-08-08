-- V1: The customer aggregate at rest, its nationality join table, and the customer-number sequence.
--
-- The schema itself is created by the Flyway instance (createSchemas + defaultSchema in
-- ModuleFlyway), not here, so that a migration never creates a schema it does not own.
--
-- PREREQUISITE, invisible in this file and load-bearing: these tables hold personal data, and they
-- must not exist while a logical replication publication carries more than account.outbox.
-- db/migration/account/V18__narrow_dbz_publication_to_outbox.sql establishes that and fails loudly
-- if it cannot. See docs/adr/009-pii-retention-and-erasure.md, D1 and D8.
--
-- Each bounded context migrates from its own Flyway instance and the two are unordered relative to each
-- other, so this file cannot be sequenced after V18 by Flyway. It does not need to be, and the reason is
-- narrower than "nothing is written yet" -- V3 seeds four policy rows during migration, so this schema
-- does produce WAL before V18 has necessarily run. What makes that safe is that those are the ONLY rows
-- any migration here writes, and they are a policy version number and three US-person rules: no personal
-- data. Both Flyway instances also complete during context refresh, before the web server accepts a
-- request, so no request-borne row can be written in the window either.
--
-- Two things therefore break the invariant, not one: shipping this schema in a release that does not
-- contain V18, and any future migration in this directory that seeds personal data.

CREATE TABLE customer.customer
(
    id                       UUID         NOT NULL,
    customer_number          VARCHAR(10)  NOT NULL,
    email                    VARCHAR(254) NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    registered_at            TIMESTAMPTZ  NOT NULL,
    activated_at             TIMESTAMPTZ,
    -- The discriminant IS the customer type: Customer.getType() derives it from which
    -- CustomerDetails subtype it holds, so a second generated "type" column would be redundant.
    kind                     VARCHAR(10)  NOT NULL,

    -- IndividualDetails. Null for a corporate, enforced by ck_customer_subtype_columns below.
    given_name               TEXT,
    middle_name              TEXT,
    family_name              TEXT,
    date_of_birth            DATE,
    gender                   VARCHAR(20),
    country_of_residence     VARCHAR(2),

    -- CorporateDetails. Null for an individual, same constraint.
    registered_name          TEXT,
    registration_number      TEXT,
    country_of_incorporation VARCHAR(2),

    CONSTRAINT pk_customer PRIMARY KEY (id),

    -- Named rather than left to PostgreSQL's <table>_<column>_key, because the persistence adapter has
    -- to tell these two apart: a duplicate email is a client-fixable conflict, whereas a duplicate
    -- customer number is a server-side collision in number issuance that the client can do nothing
    -- about. Lumping them into one "unique violation" loses that distinction.
    --
    -- uq_customer_email IS DATABASE-WIDE AND UNCONDITIONAL, and that is a decision rather than a
    -- default. Because a closed customer's row is retained for the configured CDD period, a returning
    -- applicant will hit this constraint rather than create a second record -- which is the intended
    -- outcome: one human should have one customer file, and a second file for someone already known is
    -- an AML defect, not a convenience. The path for a returning customer is therefore reinstatement of
    -- the existing record, and whichever change introduces closure owns that flow. What must not happen
    -- is the constraint being read as an accident and relaxed: the friendly answer to "this email is
    -- already registered" is a pre-check producing a field-level 400, not a weaker constraint.
    --
    -- Costed, since reversing it is the expensive direction: a partial unique index
    -- (WHERE status <> 'CLOSED') on a populated table needs CREATE INDEX CONCURRENTLY, which cannot run
    -- inside Flyway's transaction, so it is an out-of-band migration rather than a one-line edit.
    CONSTRAINT uq_customer_email UNIQUE (email),
    CONSTRAINT uq_customer_number UNIQUE (customer_number),

    -- The composite target that customer_nationality's foreign key needs. Redundant as an index
    -- given pk_customer, kept because PostgreSQL requires a unique constraint on exactly the
    -- referenced column pair.
    CONSTRAINT uq_customer_id_kind UNIQUE (id, kind),

    -- Shape only, not the Luhn check digit: the format check is what catches a truncated or padded
    -- write, and it is the half a wrong value would otherwise survive silently, since a
    -- leading-zero number read back as a number loses its width. The check digit stays the
    -- aggregate's invariant.
    CONSTRAINT ck_customer_number_shape CHECK (customer_number ~ '^[0-9]{10}$'),

    -- The email is stored canonicalised to lower case, so this is what makes uq_customer_email an
    -- actual identity constraint: a plain UNIQUE admits 'ada@example.com' and 'Ada@Example.com' side
    -- by side, which is two CDD records for one person. EmailAddress lower-cases on construction, so
    -- no domain path can produce a mixed-case row -- but this table is also reachable from a fix-up
    -- script or a bulk import, and duplicate customer identity is a KYC integrity defect rather than
    -- a cosmetic one. lower() is immutable, so it is valid in a CHECK.
    CONSTRAINT ck_customer_email_canonical CHECK (email = lower(email)),

    -- Every enumerated column gets a membership check. Without one, a bad write surfaces at read
    -- time as valueOf blowing up inside reconstitute, on the read path, far from the cause.
    CONSTRAINT ck_customer_status_known CHECK (status IN ('ONBOARDING', 'ACTIVE')),
    CONSTRAINT ck_customer_kind_known CHECK (kind IN ('INDIVIDUAL', 'CORPORATE')),
    CONSTRAINT ck_customer_gender_known CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'UNSPECIFIED')),

    -- Country codes are stored canonicalised to upper case. A lower-case row would defeat the one
    -- check that is legally non-negotiable: US-person status is a set-membership question, so
    -- 'us' silently fails to match.
    CONSTRAINT ck_customer_residence_shape CHECK (country_of_residence IS NULL OR country_of_residence ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_customer_incorporation_shape CHECK (country_of_incorporation IS NULL OR country_of_incorporation ~ '^[A-Z]{2}$'),

    -- The domain strips and rejects blank names, so a blank here is a customer record with no name
    -- that reconstitute throws on -- the same read-time failure, far from its cause, that the
    -- membership checks above exist to prevent. btrim rather than <> '' because the domain strips.
    CONSTRAINT ck_customer_required_text_not_blank CHECK (
        (given_name IS NULL OR btrim(given_name) <> '')
            AND (family_name IS NULL OR btrim(family_name) <> '')
            AND (registered_name IS NULL OR btrim(registered_name) <> '')
            AND (registration_number IS NULL OR btrim(registration_number) <> '')
        ),

    -- A static, absurd-value bound only. Not-future and minimum-age are time-varying and stay in the
    -- use case, for the same reason admission policy stays out of the CountryCode value object: a
    -- time-varying rule in a persistence constraint makes an existing row invalid the day the rule
    -- moves, and would fail on a dump/restore or a VALIDATE CONSTRAINT. PostgreSQL would accept
    -- CURRENT_DATE here; that is the trap, not the solution.
    -- Worth having even so, because at this moment nothing anywhere bounds the date: the use case
    -- that owns the real checks does not exist yet, and date of birth is an AML input.
    CONSTRAINT ck_customer_date_of_birth_plausible
        CHECK (date_of_birth IS NULL OR date_of_birth BETWEEN DATE '1900-01-01' AND DATE '2100-01-01'),

    -- Mirrors the invariant Customer.reconstitute re-asserts. Both columns are NOT NULL / nullable
    -- deliberately: a nullable status would make this biconditional evaluate to NULL, and a CHECK
    -- passes on NULL, admitting exactly the corrupt row it exists to block.
    CONSTRAINT ck_customer_active_iff_activated CHECK ((status = 'ACTIVE') = (activated_at IS NOT NULL)),

    -- A negative interval corrupts two clocks at once: the periodic-review anniversary and the AML
    -- retention anchor.
    CONSTRAINT ck_customer_activation_not_before_registration
        CHECK (activated_at IS NULL OR activated_at >= registered_at),

    -- Keeps the sealed CustomerDetails hierarchy honest at rest. Required and optional columns are
    -- distinguished: PersonalName.middleName is explicitly optional, so a blanket "every column of
    -- this subtype IS NOT NULL" would reject every registration without a middle name. On the
    -- corporate arm middle_name is listed among the columns that must be null, which is why it
    -- appears on one side and not the other.
    -- ELSE FALSE, not an implicit NULL: a CASE with no ELSE yields NULL for an unexpected kind, and
    -- the CHECK would pass.
    CONSTRAINT ck_customer_subtype_columns CHECK (
        CASE kind
            WHEN 'INDIVIDUAL' THEN given_name IS NOT NULL
                AND family_name IS NOT NULL
                AND date_of_birth IS NOT NULL
                AND gender IS NOT NULL
                AND country_of_residence IS NOT NULL
                AND registered_name IS NULL
                AND registration_number IS NULL
                AND country_of_incorporation IS NULL
            WHEN 'CORPORATE' THEN registered_name IS NOT NULL
                AND registration_number IS NOT NULL
                AND country_of_incorporation IS NOT NULL
                AND given_name IS NULL
                AND middle_name IS NULL
                AND family_name IS NULL
                AND date_of_birth IS NULL
                AND gender IS NULL
                AND country_of_residence IS NULL
            ELSE FALSE
            END
        )
    -- No version column. The aggregate version lives on customer_status_transition.sequence_no,
    -- where the audit row and the concurrency guard are the same write. A second, never-incremented
    -- counter here would be cruft that looks authoritative.
);

-- A join table rather than an array or JSONB column, for this index: "every customer holding US
-- nationality" is the FATCA report, and it is a lookup by country, not by customer.
CREATE TABLE customer.customer_nationality
(
    customer_id  UUID        NOT NULL,
    -- Denormalised from customer.kind so that "nationalities belong to individuals" is enforced
    -- rather than asserted. NOT NULL is what makes both guards work: the default MATCH SIMPLE skips
    -- the composite foreign key entirely when any referencing column is NULL, and a CHECK passes on
    -- NULL -- so a nullable kind would let a corporate acquire nationality rows and let orphan rows
    -- in, contaminating precisely the query this table exists for.
    kind         VARCHAR(10) NOT NULL,
    country_code VARCHAR(2)  NOT NULL,

    CONSTRAINT pk_customer_nationality PRIMARY KEY (customer_id, country_code),
    CONSTRAINT ck_customer_nationality_individuals_only CHECK (kind = 'INDIVIDUAL'),
    CONSTRAINT ck_customer_nationality_country_shape CHECK (country_code ~ '^[A-Z]{2}$'),

    -- CASCADE, unlike the audit tables' RESTRICT, and the asymmetry is the point: nationalities are
    -- part of the customer's state and should die with it, whereas an audit row is evidence *about*
    -- the customer and must survive it (ADR-009 D5).
    -- This composite foreign key subsumes a plain one on customer_id; only one is declared.
    -- No foreign key on country_code to any country list: that would re-introduce time-variance at
    -- the schema level, so de-listing a country would break writes for existing customers.
    CONSTRAINT fk_customer_nationality_customer
        FOREIGN KEY (customer_id, kind) REFERENCES customer.customer (id, kind) ON DELETE CASCADE
);

CREATE INDEX idx_customer_nationality_country_code
    ON customer.customer_nationality (country_code);

-- The Nationalities value object requires between 1 and 10 distinct codes, and neither bound is a
-- single-row property, so neither is expressible as a CHECK. A deferred constraint trigger is: it
-- evaluates at COMMIT, by which point the customer row and its nationality rows have both been
-- written.
--
-- The lower bound is the one that matters. An individual with zero nationality rows is not merely
-- incomplete, it is UNREHYDRATABLE -- every read of it constructs Nationalities, which throws, so the
-- row becomes a permanent 500 on the read path. Nationality is also a FATCA-reported field, so an
-- empty set is a reporting gap rather than a cosmetic one.
--
-- Rejected alternative: an `ordinal` column with UNIQUE (customer_id, ordinal) and a 0..9 CHECK. It
-- bounds only the upper end, and it would put an order on this table that the domain explicitly
-- denies exists -- Nationalities is a Set precisely because nationality is unranked, and the mapper
-- would have to invent ordinal values that mean nothing.
--
-- Consequence worth stating, because it constrains the write path: the customer row and its
-- nationality rows must be written in the SAME transaction. That is already the intended design, and
-- this makes a partial aggregate unrepresentable rather than merely unintended.
--
-- 🔴 THE ROW LOCK BELOW IS WHAT MAKES THIS HOLD UNDER CONCURRENCY -- do not "simplify" it back to an
-- EXISTS check. A COUNT(*) over a set does not serialise itself: nothing stops two transactions' trigger
-- bodies from interleaving so that each counts a row the other is in the middle of deleting, both see 1,
-- both pass, and the customer commits holding none -- the unrehydratable row this guard exists to
-- prevent. Locking the parent first makes the bodies mutually exclusive per customer, so the second one
-- re-reads after the first commits and refuses.
--
-- SCOPE THE RACE HONESTLY, because it is narrower than it first looks and an overstated version of this
-- comment would invite the lock's removal. At READ COMMITTED the ordinary schedule is already safe: the
-- later transaction's count is a new statement, so it takes a fresh snapshot and sees the earlier
-- committed delete. The window the lock closes is the one where the second count lands between the
-- first count and the first COMMIT. Verified: two concurrent deletes driven from the client cannot
-- reproduce it, which is why the test for this asserts the lock's observable effect -- that a nationality
-- change contends for the customer row -- rather than racing two deletes and passing either way.
--
-- Scope the FIX honestly too: the lock closes this at READ COMMITTED only. At REPEATABLE READ or above
-- the lock is still granted but the count reads the transaction's older snapshot, so the hole reopens and
-- SERIALIZABLE's predicate locking would be the mechanism instead. This project pins no isolation level
-- and therefore runs READ COMMITTED, which the activation design also depends on; revisit both together
-- if one is ever configured.
CREATE FUNCTION customer.assert_nationality_cardinality()
    RETURNS trigger
    LANGUAGE plpgsql
AS
$$
DECLARE
    -- The trigger is attached to two tables that name the customer differently, so the column is
    -- passed as an argument and read through jsonb rather than duplicating the function. PL/pgSQL leaves
    -- OLD null on INSERT and NEW null on DELETE, so exactly one of these is null in those cases.
    old_subject UUID := (to_jsonb(OLD) ->> TG_ARGV[0])::UUID;
    new_subject UUID := (to_jsonb(NEW) ->> TG_ARGV[0])::UUID;
    subject_id  UUID;
    held        INTEGER;
BEGIN
    -- A misnamed TG_ARGV column yields null for the record that does exist, and every check below would
    -- then match nothing -- disabling the guard behind a green migration. Fail loudly instead.
    IF (TG_OP <> 'DELETE' AND new_subject IS NULL)
        OR (TG_OP <> 'INSERT' AND old_subject IS NULL) THEN
        RAISE EXCEPTION 'trigger % is misconfigured: no column named %', TG_NAME, TG_ARGV[0];
    END IF;

    -- 🔴 BOTH subjects, not just the new one. An UPDATE that moves a nationality row from one customer
    -- to another affects two sets, and checking only NEW leaves the customer it was taken FROM
    -- unvalidated -- so a single UPDATE could strip an individual's last nationality and commit, which
    -- is the state this guard exists to make impossible. It is the same shape as a DELETE, which is
    -- refused, so checking one side would leave the file documenting a guarantee it does not provide.
    -- DISTINCT collapses the ordinary UPDATE that does not move the row. ORDER BY makes two reparenting
    -- transactions take the two locks in the same order WITHIN ONE INVOCATION -- verified: without it,
    -- moving d->e and e->d lock in opposite orders. It is not a global order: a transaction that touches
    -- two customers across several statements, or that holds a lock this trigger then wants, can still
    -- deadlock at COMMIT. No current write path does either -- registration and activation are both
    -- single-customer -- and the client-visible symptom would be 40P01 raised by COMMIT rather than by a
    -- statement, so a retry has to wrap the whole transaction.
    FOR subject_id IN
        SELECT DISTINCT candidate
          FROM unnest(ARRAY [old_subject, new_subject]) AS candidate
         WHERE candidate IS NOT NULL
         ORDER BY candidate
        LOOP
            -- Locks the parent AND serves as the "is this an individual that still exists" test in one
            -- statement. The customer is allowed to be gone: customer_nationality cascades on delete, so
            -- this fires once per cascaded row after the parent has been removed.
            PERFORM 1
               FROM customer.customer
              WHERE id = subject_id
                AND kind = 'INDIVIDUAL'
                FOR UPDATE;

            IF FOUND THEN
                SELECT count(*) INTO held
                  FROM customer.customer_nationality
                 WHERE customer_id = subject_id;

                IF held < 1 OR held > 10 THEN
                    RAISE EXCEPTION
                        'individual customer % must hold between 1 and 10 nationalities, found %',
                        subject_id, held
                    USING HINT = 'Write a customer row and its nationality rows in one transaction, and'
                        ' never move the last one off a customer.';
                END IF;
            END IF;
        END LOOP;

    RETURN NULL;
END;
$$;

-- On customer as well as on the child, so that inserting an individual with no nationalities at all
-- is caught. UPDATE is included because flipping kind from CORPORATE to INDIVIDUAL would otherwise
-- produce an individual holding none.
CREATE CONSTRAINT TRIGGER trg_customer_nationality_cardinality
    AFTER INSERT OR UPDATE
    ON customer.customer
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION customer.assert_nationality_cardinality('id');

-- DELETE included here: removing rows is how the lower bound gets violated after the fact.
CREATE CONSTRAINT TRIGGER trg_customer_nationality_cardinality
    AFTER INSERT OR UPDATE OR DELETE
    ON customer.customer_nationality
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION customer.assert_nationality_cardinality('customer_id');

-- ENABLE ALWAYS, for the same reason the append-only guards carry it: a single
-- `SET session_replication_role = replica` silences a trigger at its default enable state, and it is a
-- legitimate mode rather than only an attack -- logical-replication apply workers run in it, and restore
-- and backfill tooling commonly sets it precisely to stop triggers firing. Any such path would
-- otherwise write the state this constraint exists to forbid.
--
-- This is a CORRECTNESS constraint rather than a write guard, and it needs the treatment just as much:
-- an individual left holding no nationalities is unrehydratable, and the damage self-seals, because
-- every later UPDATE to that customer then fails on this same trigger.
--
-- ENABLE ALWAYS is accepted on a CONSTRAINT TRIGGER and leaves DEFERRABLE INITIALLY DEFERRED intact.
ALTER TABLE customer.customer
    ENABLE ALWAYS TRIGGER trg_customer_nationality_cardinality;

ALTER TABLE customer.customer_nationality
    ENABLE ALWAYS TRIGGER trg_customer_nationality_cardinality;

-- Source for the 9-digit body of a customer number, which is then format-preserving-encrypted and
-- given a Luhn check digit.
--   START WITH 1, not 0: the encryption's cycle walk is a bijection on [1, 10^9) only if its input
--     already excludes 0, so this bound is half of that guarantee and not a cosmetic choice.
--   MAXVALUE 999999999: at 10^9 the body would gain a digit and the number would stop being 10
--     digits wide.
--   NO CYCLE so exhaustion raises instead of silently reissuing numbers already in use.
CREATE SEQUENCE customer.customer_number_seq
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 999999999
    NO CYCLE;
