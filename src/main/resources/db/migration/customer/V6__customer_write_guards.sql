-- V6: Writes the application must not perform -- append-only on the four audit tables, deletion of a
-- customer, truncation of the nationality child, and removal of the policy version row. They all use the
-- same mechanism, so they live together and there is one file to audit.
--
-- These tables carry retention-bound evidence, so append-only has to be a constraint rather than a
-- convention. ADR-009 D5 records the verification behind the shape below; the short version is that
-- there are four ways to defeat append-only silently and only one mechanism closes all of them:
--
--   1. A REVOKE against the runtime role does nothing here. The application, Flyway and Debezium all
--      connect as the bootstrap superuser, which owns every table Flyway creates, and a superuser
--      bypasses privilege checks entirely. There is no non-owning role on the write path to revoke
--      from -- so this is not "a control one careless GRANT could undo", it is not a control.
--   2. Referential actions are not privilege-checked against the caller: PostgreSQL implements them
--      with internal triggers running as the table owner, so ON DELETE CASCADE deletes rows the
--      caller is explicitly forbidden to delete, with no error. Hence RESTRICT on every audit
--      foreign key.
--   3. TRUNCATE ... CASCADE defeats both RESTRICT and row-level triggers, because row triggers do not
--      fire on TRUNCATE and the referential action is not consulted. Only a statement-level BEFORE
--      TRUNCATE trigger stops it.
--   4. SET session_replication_role = replica defeats triggers AND referential actions in one
--      statement, leaving an orphaned audit row pointing at a deleted parent. It is SUSET, so per (1)
--      this application can issue it. ENABLE ALWAYS restores the guard under replica mode.
--
-- So: BEFORE UPDATE and BEFORE DELETE row triggers, plus a statement-level BEFORE TRUNCATE trigger,
-- all ENABLE ALWAYS. ENABLE ALWAYS is not a CREATE TRIGGER clause -- attempting it is a syntax error
-- -- so each trigger needs a following ALTER TABLE.
--
-- SCOPE THE CLAIM HONESTLY, since the above has just demolished one overstated control. Triggers are
-- not unbypassable by a superuser: DROP TRIGGER, DISABLE TRIGGER and DROP COLUMN all defeat them, and
-- the last is worth naming -- dropping admission_decision_match.triggering_country fires nothing,
-- leaves every row in place, and erases the evidence they exist to hold. What triggers buy is that
-- every remaining path is a deliberate, DDL-visible, auditable act, whereas a REVOKE against an
-- owning superuser requires no act at all and leaves no trace.
--
-- MAINTENANCE, INCLUDING THE FUTURE RETENTION PURGE, suspends the guard with
--   ALTER TABLE customer.<table> DISABLE TRIGGER <name>;
-- inside its transaction. That idiom still works against an ENABLE ALWAYS trigger, takes only a
-- ShareRowExclusiveLock, and rolls back with the transaction. It must NEVER use
-- session_replication_role: the two look interchangeable, but one is the maintenance path and the
-- other is bypass (4), which ENABLE ALWAYS deliberately breaks.
--
-- processed_registrations is deliberately absent: it is prunable operational data, not evidence.

CREATE FUNCTION customer.audit_append_only()
    RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    RAISE EXCEPTION '%.% is append-only. % is not allowed.',
        TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
        USING HINT =
            'Maintenance must use ALTER TABLE ... DISABLE TRIGGER inside its transaction, never '
            'session_replication_role.';
END;
$$;

-- Driven from one list rather than twelve hand-written trigger pairs. The list is the thing to
-- review, and a copy-pasted table name silently leaving one audit table unguarded is the realistic
-- failure here -- exactly the mistake this file exists to prevent elsewhere.
DO
$$
    DECLARE
        audit_table TEXT;
    BEGIN
        FOREACH audit_table IN ARRAY ARRAY [
            'customer_status_transition',
            'admission_decision',
            'admission_decision_match',
            'customer_admission'
            ]
            LOOP
                EXECUTE format(
                        'CREATE TRIGGER trg_%1$s_append_only_update BEFORE UPDATE ON customer.%1$I'
                            || ' FOR EACH ROW EXECUTE FUNCTION customer.audit_append_only()',
                        audit_table);
                EXECUTE format(
                        'CREATE TRIGGER trg_%1$s_append_only_delete BEFORE DELETE ON customer.%1$I'
                            || ' FOR EACH ROW EXECUTE FUNCTION customer.audit_append_only()',
                        audit_table);
                EXECUTE format(
                        'CREATE TRIGGER trg_%1$s_append_only_truncate BEFORE TRUNCATE ON customer.%1$I'
                            || ' FOR EACH STATEMENT EXECUTE FUNCTION customer.audit_append_only()',
                        audit_table);

                EXECUTE format(
                        'ALTER TABLE customer.%1$I'
                            || ' ENABLE ALWAYS TRIGGER trg_%1$s_append_only_update,'
                            || ' ENABLE ALWAYS TRIGGER trg_%1$s_append_only_delete,'
                            || ' ENABLE ALWAYS TRIGGER trg_%1$s_append_only_truncate',
                        audit_table);
            END LOOP;
    END;
$$;

-- ---------------------------------------------------------------------------------------------
-- DELETING A CUSTOMER IS A PRIVILEGED OPERATION, NOT AN APPLICATION CAPABILITY.
--
-- The application never deletes a customer; erasure is a privileged, ordered, audited purge job that
-- does not exist yet. Until now the only thing resembling a guard was indirect and had a hole named in
-- ADR-009 D5: the audit foreign keys are ON DELETE RESTRICT, but an ONBOARDING customer has ZERO
-- transition rows, so RESTRICT never engages for them. `DELETE FROM customer.customer WHERE status =
-- 'ONBOARDING'` therefore succeeded, cascaded the nationalities away, and left no trace -- and per
-- ADR-009 D3 the abandoned-onboarding population is likely the largest personal-data category in this
-- schema.
--
-- Deliberately DELETE and TRUNCATE only. Unlike the audit tables, customer.customer is mutable: status
-- and activated_at are updated by activation, so a BEFORE UPDATE guard here would block the one write
-- the aggregate exists to make.
--
-- WHAT THIS FILE'S OWN LOGIC IMPLIES NEXT, recorded so it is a decision rather than an omission: only
-- status and activated_at are supposed to change after registration, yet customer_number, kind, email,
-- date_of_birth and the name columns are all freely updatable, with no transition row written. In most
-- regimes a change of name or date of birth is a KYC re-verification event, not a field edit. The
-- precise guard is a BEFORE UPDATE trigger that rejects changes to those columns specifically while
-- letting status and activated_at through -- not a blanket one. It is not built here because the
-- mutating paths themselves do not exist yet; it belongs with the first endpoint that edits a customer.
-- One column is already covered for part of the population, as a side effect rather than by design:
-- customer_admission's composite foreign key references (id, kind), so an admitted customer's kind
-- cannot be flipped while the link exists. That guard therefore has one fewer column to cover for
-- customers who have been admitted, and none fewer for anyone still onboarding.
CREATE FUNCTION customer.deletion_is_privileged()
    RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    RAISE EXCEPTION '%.% may not be deleted by the application. % is not allowed.',
        TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
        USING HINT =
            'Erasure is a privileged retention purge, which suspends this guard with ALTER TABLE ... '
            'DISABLE TRIGGER inside its transaction, never with session_replication_role.';
END;
$$;

CREATE TRIGGER trg_customer_no_delete
    BEFORE DELETE
    ON customer.customer
    FOR EACH ROW
EXECUTE FUNCTION customer.deletion_is_privileged();

CREATE TRIGGER trg_customer_no_truncate
    BEFORE TRUNCATE
    ON customer.customer
    FOR EACH STATEMENT
EXECUTE FUNCTION customer.deletion_is_privileged();

ALTER TABLE customer.customer
    ENABLE ALWAYS TRIGGER trg_customer_no_truncate,
    ENABLE ALWAYS TRIGGER trg_customer_no_delete;

-- customer_nationality needs the TRUNCATE half too, and only that half.
--
-- Bypass (3) above, applied to a table this file would otherwise not cover: row triggers do not fire on
-- TRUNCATE, and the deferred cardinality trigger that catches `DELETE FROM customer.customer_nationality`
-- is a row trigger. So a direct TRUNCATE of the child removes every nationality, fires nothing, leaves
-- the customer rows in place, and makes every individual unrehydratable while the FATCA-by-country query
-- silently returns nothing. Verified on PostgreSQL 18. No race and no privilege needed -- one statement.
--
-- DELETE is deliberately not guarded here: these rows are part of the customer's mutable state and are
-- meant to cascade when the customer is purged. The cardinality trigger already refuses a delete that
-- would leave an individual with none.
CREATE TRIGGER trg_customer_nationality_no_truncate
    BEFORE TRUNCATE
    ON customer.customer_nationality
    FOR EACH STATEMENT
EXECUTE FUNCTION customer.deletion_is_privileged();

ALTER TABLE customer.customer_nationality
    ENABLE ALWAYS TRIGGER trg_customer_nationality_no_truncate;

-- The policy version row must never be removed, and without this its monotonicity trigger is trivially
-- defeated.
--
-- V3 guards the version with a BEFORE UPDATE trigger asserting it advances, which is only as good as the
-- row's permanence: DELETE then re-INSERT at version 1, or TRUNCATE then re-INSERT, walks the version
-- backwards in two statements without ever performing an UPDATE. That produces exactly what V3 warns
-- about -- one version number describing two different rule sets -- and makes every
-- admission_decision.policy_version citing it ambiguous.
--
-- There is no legitimate delete: the table holds one row for the lifetime of the schema, so guarding both
-- paths costs nothing.
CREATE TRIGGER trg_admission_policy_no_delete
    BEFORE DELETE
    ON customer.admission_policy
    FOR EACH ROW
EXECUTE FUNCTION customer.deletion_is_privileged();

CREATE TRIGGER trg_admission_policy_no_truncate
    BEFORE TRUNCATE
    ON customer.admission_policy
    FOR EACH STATEMENT
EXECUTE FUNCTION customer.deletion_is_privileged();

ALTER TABLE customer.admission_policy
    ENABLE ALWAYS TRIGGER trg_admission_policy_no_delete,
    ENABLE ALWAYS TRIGGER trg_admission_policy_no_truncate;

-- ---------------------------------------------------------------------------------------------
-- Assert the outcome, not that the statements above ran.
--
-- tgenabled = 'A' is ENABLE ALWAYS; the default 'O' is the state bypass (4) walks straight through, and
-- the difference between the two is invisible in a green migration. Matching on the guard FUNCTIONS
-- rather than on a trigger-name pattern keeps this honest as guards are added: a new guard using either
-- function is counted whatever it is called, and the other triggers in this schema -- the policy-version
-- check and the deferred cardinality constraints, which are correctness rules rather than write guards
-- -- are excluded by construction rather than by a naming coincidence.
--
-- 17 = 4 audit tables x (UPDATE, DELETE, TRUNCATE), plus DELETE and TRUNCATE on customer, plus TRUNCATE
-- on customer_nationality, plus DELETE and TRUNCATE on admission_policy.
--
-- The count is deliberately database-wide rather than filtered to this schema: these two functions exist
-- only to guard these tables, so a trigger using either of them anywhere else is something to notice
-- rather than to exclude, and it pushes the count off 17 and fails the migration.
DO
$$
    DECLARE
        always_enabled INT;
    BEGIN
        SELECT count(*)
          INTO always_enabled
          FROM pg_trigger t
         WHERE NOT t.tgisinternal
           AND t.tgenabled = 'A'
           AND t.tgfoid IN ('customer.audit_append_only'::regproc,
                            'customer.deletion_is_privileged'::regproc);

        IF always_enabled <> 17 THEN
            RAISE EXCEPTION
                'expected 17 ENABLE ALWAYS write guards backed by the customer guard functions, found %.',
                always_enabled;
        END IF;
    END;
$$;

-- ---------------------------------------------------------------------------------------------
-- 🔴 AND THE ASSERTION THAT MAKES THE ONE ABOVE SUFFICIENT: no trigger in this schema, of any kind, may
-- sit at its default enable state.
--
-- The count above enumerates what IS ENABLE ALWAYS, so by construction it can never notice a trigger
-- that is not -- and that is exactly how four correctness triggers came to be silenceable by one SET
-- while every write guard was correctly hardened. The append-only guards were hardened because bypass
-- (4) is documented against the audit tables; the deferred CONSTRAINT TRIGGERs enforcing nationality
-- cardinality and decision/match agreement were not, because nothing was checking for the absence.
--
-- This query is the complement, and it is exhaustive over the catalog rather than over a list somebody
-- has to remember to extend. Every trigger a future migration adds to this schema is covered on the day
-- it lands, whether it is a write guard, a correctness constraint, or something nobody has thought of
-- yet -- which is the property a maintained table-name list cannot have.
--
-- If a trigger ever genuinely must be silenceable by replica mode, this is the line that forces that to
-- be argued for rather than defaulted into.
DO
$$
    DECLARE
        silenceable TEXT;
    BEGIN
        SELECT string_agg(format('%s.%s', c.relname, t.tgname), ', ' ORDER BY c.relname, t.tgname)
          INTO silenceable
          FROM pg_trigger t
                   JOIN pg_class c ON c.oid = t.tgrelid
                   JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'customer'
           AND NOT t.tgisinternal
           AND t.tgenabled <> 'A';

        IF silenceable IS NOT NULL THEN
            RAISE EXCEPTION
                'triggers in the customer schema are not ENABLE ALWAYS, so one SET'
                ' session_replication_role = replica silences them: %.', silenceable
            USING HINT =
                'Add ALTER TABLE customer.<table> ENABLE ALWAYS TRIGGER <name> beside the CREATE, or'
                ' argue in the migration why this one may be bypassed.';
        END IF;
    END;
$$;

-- ---------------------------------------------------------------------------------------------
-- DECLARED INTENT, NOT YET A CONTROL.
--
-- Once a non-owning application role exists, the statements below become real defence in depth. They
-- are written here rather than executed because there is nothing to revoke from today: no such role
-- exists, and REVOKE against a role that does not exist is an error, while REVOKE ... FROM PUBLIC
-- succeeds and does nothing -- which would put a line in this file that reads as a control and
-- provably is not. That is the precise mistake ADR-009 D5 exists to correct, so it is not repeated
-- here in a weaker form.
--
--   REVOKE UPDATE, DELETE, TRUNCATE ON customer.customer_status_transition FROM <app_role>;
--   REVOKE UPDATE, DELETE, TRUNCATE ON customer.admission_decision        FROM <app_role>;
--   REVOKE UPDATE, DELETE, TRUNCATE ON customer.admission_decision_match  FROM <app_role>;
--   REVOKE UPDATE, DELETE, TRUNCATE ON customer.customer_admission        FROM <app_role>;
--   REVOKE DELETE ON customer.customer FROM <app_role>;
--
-- No design in this repository may cite a GRANT or REVOKE as an enforcement mechanism until the
-- write path stops running as the owning superuser.
