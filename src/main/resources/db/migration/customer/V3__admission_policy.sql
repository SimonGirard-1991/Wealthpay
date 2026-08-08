-- V3: Country admission policy -- who we will onboard, as opposed to which country codes exist.
--
-- Those two questions are split deliberately. "Is FR a real ISO 3166-1 code" never changes and is
-- answered in process by the CountryCode value object. "Do we onboard this subject" changes weekly
-- with sanctions designations and licence grants, so it is data, read through a port and evaluated
-- by the application. Putting the second question in the value object would make persisted rows
-- un-rehydratable the day compliance edits a list: the repository would load a row and the
-- constructor would throw, so history would depend on present-day policy.
--
-- TWO TABLES, BECAUSE POLARITY IS STRUCTURAL AND MUST NEVER BECOME A COLUMN.
-- The deny side fails open when a row is missing: forget a sanctioned country and we onboard someone
-- we are prohibited from serving. The allow side fails closed: forget a licensed country and we
-- decline business we could have taken, which is embarrassing and recoverable. Sanctions have to be
-- a deny-list because "every country that is not sanctioned" is not enumerable, and licensing has to
-- be an allow-list because the failure has to land on the recoverable side. A single table with a
-- polarity column would let one mis-seeded row silently invert a rule; two tables make that
-- unrepresentable.
--
-- The connecting factor stays data rather than becoming a branch. Every restriction declares which
-- attribute of the subject ties it to a country, so extending the model is a row rather than a code
-- change -- see the seeded rows at the bottom of this file for what that buys.

-- DENY side.
CREATE TABLE customer.restricted_country
(
    country_code      VARCHAR(2)  NOT NULL,
    restriction       VARCHAR(20) NOT NULL,
    connecting_factor VARCHAR(20) NOT NULL,

    CONSTRAINT pk_restricted_country PRIMARY KEY (country_code, restriction, connecting_factor),
    CONSTRAINT ck_restricted_country_code_shape CHECK (country_code ~ '^[A-Z]{2}$'),

    -- UNLICENSED is deliberately not admissible here: it has allow-list polarity and is only ever
    -- produced by evaluating licensed_country. A row for it on the deny side means the two tables
    -- have been crossed, which is the mistake that inverts a rule. AdmissionPolicySnapshot rejects
    -- the same value on read, so this is the write-side half of one rule.
    CONSTRAINT ck_restricted_country_restriction_known
        CHECK (restriction IN ('SANCTIONED', 'RESTRICTED_PERSON')),
    CONSTRAINT ck_restricted_country_factor_known
        CHECK (connecting_factor IN ('NATIONALITY', 'RESIDENCE', 'INCORPORATION'))
    -- No foreign key from country_code to any country list: this table keys on a time-varying set,
    -- and a foreign key here would undo the existence-versus-admission split above.
);

-- ALLOW side: where we are authorised, and for whom.
CREATE TABLE customer.licensed_country
(
    country_code      VARCHAR(2)  NOT NULL,
    -- Not ceremony: it is what says which field of the subject to read. An individual has no country
    -- of incorporation and a legal entity has no residence, so without the factor the allow-list
    -- cannot be evaluated against a corporate subject at all. ('FR','RESIDENCE') present with
    -- ('FR','INCORPORATION') absent means we may serve individuals resident in France while holding
    -- no permission covering entities established there.
    connecting_factor VARCHAR(20) NOT NULL,

    CONSTRAINT pk_licensed_country PRIMARY KEY (country_code, connecting_factor),
    CONSTRAINT ck_licensed_country_code_shape CHECK (country_code ~ '^[A-Z]{2}$'),
    -- NATIONALITY is absent by design. Licensing is about where the activity happens, so a French
    -- resident who holds the nationality of a country we merely lack a licence in is not refused:
    -- we lack permission to serve them there, and they are here.
    CONSTRAINT ck_licensed_country_factor_known
        CHECK (connecting_factor IN ('RESIDENCE', 'INCORPORATION'))
);

-- One row, covering BOTH tables above.
--
-- List-level rather than a version column per country, because a de-listed country contributes no
-- row at all, so a per-row version cannot record that it was de-listed -- the change is
-- structurally invisible. Not the Flyway version either: flyway_schema_history is Flyway-owned
-- state, and reading it as policy metadata couples a compliance record to a migration tool's
-- bookkeeping.
--
-- The recorded admission decision must read this in the same statement as the rules it applied, or
-- it documents a policy that was never the one applied.
--
-- CONVENTION: any later migration that adds, removes or changes a row in either table above bumps
-- this version in the same migration. A policy change that leaves the version untouched makes every
-- historical decision row claim a policy it was not evaluated against.
--
-- WHERE THE CONTENT OF VERSION N LIVES, AND THE CONSTRAINT THAT FOLLOWS.
-- A refused decision is self-contained: its match rows record which rules fired. An ADMITTED decision
-- is not -- its evidence is "screened clean against version N", and it carries no match rows, so
-- reconstructing what N contained is a question the schema alone cannot answer. That reconstruction
-- exists, but it lives outside the database: seeding these tables through versioned migrations is a
-- deliberate choice precisely because it makes the rule set reviewable and gives a free audit trail
-- for "who removed RU, and when". Version N's content is the seed migrations up to the one that set
-- N.
--
-- That mechanism is why no in-database policy snapshot table is carried here, and it is only as good
-- as its premise: THE MOMENT ANY PATH EDITS THESE TABLES OUTSIDE A MIGRATION -- the admin UI that
-- weekly sanctions updates will eventually demand -- the reconstruction is gone, and that change must
-- bring an append-only snapshot of each version's rules with it. It is not an optional extra to
-- sequence afterwards; without it, an admitted customer's screening evidence becomes unrecoverable.
--
-- ACCEPTED RESIDUAL, stated rather than left silent: nothing in the database enforces that premise.
-- restricted_country and licensed_country are deliberately NOT write-guarded, because they are meant to
-- change and every change is meant to arrive as a migration. So a direct DELETE or TRUNCATE against
-- either -- by anyone with a psql session -- silently falsifies the reconstruction above for every
-- admitted decision, with no trace. It is not guarded here for two reasons: a guard would force each
-- future de-listing migration through the DISABLE TRIGGER idiom, and it would be removed anyway by the
-- admin-UI change that owns the snapshot table. The exposure is bounded today because an empty
-- licensed_country refuses every applicant, so no one can be wrongly admitted while it stays empty.
-- Revisit with the snapshot table, not before.
CREATE TABLE customer.admission_policy
(
    -- The singleton idiom: one row, enforced by the schema rather than by everyone remembering.
    singleton BOOLEAN NOT NULL DEFAULT TRUE,
    version   BIGINT  NOT NULL,

    CONSTRAINT pk_admission_policy PRIMARY KEY (singleton),
    CONSTRAINT ck_admission_policy_single_row CHECK (singleton),
    CONSTRAINT ck_admission_policy_version_positive CHECK (version > 0)
);

-- Monotonicity is enforced rather than assumed. This table is deliberately not append-only -- the
-- version is a counter, so it has to be updatable -- but CHECK (version > 0) cannot see the previous
-- value, so nothing otherwise stops the version being walked backwards. If it were, one version
-- number would describe two different rule sets and every admission_decision row citing it would
-- become ambiguous, which is the failure the header comment above warns about.
CREATE FUNCTION customer.assert_policy_version_advances()
    RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF NEW.version <= OLD.version THEN
        RAISE EXCEPTION
            'admission policy version must advance: % is not greater than %', NEW.version, OLD.version
        USING HINT =
            'A version number identifies one rule set for the lifetime of the decisions that cite it.';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_admission_policy_version_advances
    BEFORE UPDATE
    ON customer.admission_policy
    FOR EACH ROW
EXECUTE FUNCTION customer.assert_policy_version_advances();

ALTER TABLE customer.admission_policy
    ENABLE ALWAYS TRIGGER trg_admission_policy_version_advances;

INSERT INTO customer.admission_policy (version)
VALUES (1);

-- ---------------------------------------------------------------------------------------------
-- SEED: the restricted-person rule for US persons.
--
-- Three rows and no code, which is the whole argument for the connecting factor being data. Under
-- three flat lists the third row below would have needed a new table, a new evaluation branch and a
-- new enum value.
--
-- Refusing US persons outright is standard practice at non-US firms and is more conservative than
-- either regime strictly requires. The driver is the cost of the FATCA reporting obligation, not a
-- licensing prohibition -- the two regimes key on different things and must not be merged:
--
--   FATCA  (IRC s.7701(a)(30); green card via s.7701(b)(6)) keys on CITIZENSHIP wherever the person
--          is resident, plus US tax residence, plus US-incorporated entities under
--          s.7701(a)(30)(C). This is what justifies the NATIONALITY row: a reporting burden that
--          follows the passport around the world.
--   Reg S  (17 CFR s.230.902(k)) keys on RESIDENCE for natural persons and INCORPORATION for
--          entities. Its natural-person limb would not produce the NATIONALITY row at all.
--
-- The INCORPORATION row is the one worth defending, because an earlier draft of this design omitted
-- it: a US company IS a US person. Reg S Rule 902(k)(1)(ii) puts any corporation organised under the
-- laws of the United States inside "U.S. person", and IRC s.7701(a)(30)(C) does the same for FATCA.
-- Without it a Delaware corporation would be screened against sanctions and licensing and never
-- against the restricted-person rule at all.
--
-- SCOPE THIS HONESTLY -- this seed is not "US person handled". It covers three country-shaped limbs.
-- Green-card holders are a status with no country field to hang off, and the Model IGA Annex I
-- indicia (US place of birth, address, phone, standing payment instructions) are not covered either.
-- Both need model changes, not seed rows.
INSERT INTO customer.restricted_country (country_code, restriction, connecting_factor)
VALUES ('US', 'RESTRICTED_PERSON', 'NATIONALITY'),
       ('US', 'RESTRICTED_PERSON', 'RESIDENCE'),
       ('US', 'RESTRICTED_PERSON', 'INCORPORATION');

-- ---------------------------------------------------------------------------------------------
-- DELIBERATELY NOT SEEDED YET, and this is a blocker rather than an omission.
--
-- Both remaining seed sets depend on the home authorisation jurisdiction, which is not recorded
-- anywhere in this repository:
--
--   licensed_country       -- whether this is roughly thirty EEA passport rows or a handful of local
--                             licences depends entirely on where we are authorised. Every row would
--                             be an unvalidated guess, and an over-broad allow-list is a regulatory
--                             breach rather than an inconvenience.
--   restricted_country     -- which sanctions regime binds us (EU, UK, UN, or several) is the same
--     SANCTIONED rows         question. Sanctions screening itself is vendor territory in any case:
--                             fuzzy name matching, PEP lists and daily deltas are not a table.
--
-- WHILE licensed_country IS EMPTY, EVERY APPLICANT IS REFUSED. That is the allow-list polarity doing
-- exactly what it was chosen to do, and it is why a partially seeded deny-list is safe here: nothing
-- can be admitted, so the missing sanctions rows cannot let anyone through. It also means the
-- refusal is invisible to the client, since a refusal body is deliberately generic -- so the startup
-- assertions and the admission-outcome metric are what make this state legible to an operator, and
-- they are a prerequisite for serving traffic, not a nicety.
