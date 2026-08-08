package org.girardsimon.wealthpay.customer.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.UUID;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;

/**
 * The constraints are this migration's deliverable, so they are what is tested. Every one of them
 * exists because the alternative was to assert an invariant in a comment and hope, and a constraint
 * that does not actually bite is worse than none — it reads as a guarantee.
 *
 * <p>Several assertions here are regression tests for traps verified against PostgreSQL 18 rather
 * than hypotheticals: a nullable column makes a {@code CHECK} over it evaluate to NULL, which
 * passes; a {@code CASE} with no {@code ELSE} does the same; {@code MATCH SIMPLE} skips a composite
 * foreign key entirely when any referencing column is NULL; and {@code array_to_string} silently
 * omits NULL elements, so a regex over it accepts an array containing one.
 */
@JooqTest
class CustomerSchemaConstraintsTest extends AbstractCustomerContainerTest {

  private static final String INDIVIDUAL_ID = "11111111-1111-1111-1111-111111111111";
  private static final String CORPORATE_ID = "33333333-3333-3333-3333-333333333333";

  @Autowired private DataSource dataSource;

  private SchemaProbe probe;

  @BeforeEach
  void setUp() {
    probe = new SchemaProbe(dataSource);
    // Idempotent, because rejected statements need no cleanup and the audit tables cannot be
    // cleaned.
    // The numbers are Luhn-valid and carry leading zeros, which is the shape the generator emits.
    probe.executeAll(
        """
        INSERT INTO customer.customer
          (id, customer_number, email, status, registered_at, kind,
           given_name, family_name, date_of_birth, gender, country_of_residence)
        VALUES ('%s', '0000000018', 'ada@example.com', 'ONBOARDING', '2026-01-01T00:00:00Z',
                'INDIVIDUAL', 'Ada', 'Lovelace', '1990-01-01', 'FEMALE', 'FR')
        ON CONFLICT DO NOTHING
        """
            .formatted(INDIVIDUAL_ID),
        nationality(INDIVIDUAL_ID, "FR"));
    probe.execute(
        """
        INSERT INTO customer.customer
          (id, customer_number, email, status, registered_at, kind,
           registered_name, registration_number, country_of_incorporation)
        VALUES ('%s', '0000000034', 'acme@example.com', 'ONBOARDING', '2026-01-01T00:00:00Z',
                'CORPORATE', 'Acme SA', 'RCS-123', 'FR')
        ON CONFLICT DO NOTHING
        """
            .formatted(CORPORATE_ID));
  }

  /**
   * The trap in the discriminant check: {@code PersonalName.middleName} is optional, so a blanket
   * "every column of this subtype IS NOT NULL" would reject every registration without a middle
   * name — a defect no fixture that happens to supply one can see.
   */
  @Test
  void an_individual_without_a_middle_name_is_accepted() {
    // Arrange
    UUID id = UUID.randomUUID();

    // Act
    probe.executeAll(
        individual(id, "0000000026", "alan@example.com", null), nationality(id.toString(), "GB"));

    // Assert
    assertThat(
            probe.count(
                "SELECT count(*) FROM customer.customer WHERE id = '%s' AND middle_name IS NULL"
                    .formatted(id)))
        .isEqualTo(1);
  }

  @Test
  void the_active_and_activation_instant_biconditional_is_enforced_in_both_directions() {
    // Arrange
    String activeWithoutInstant =
        corporate(UUID.randomUUID(), "0000000042", "a1@example.com", "'ACTIVE'", "NULL");
    String onboardingWithInstant =
        corporate(
            UUID.randomUUID(),
            "0000000059",
            "a2@example.com",
            "'ONBOARDING'",
            "'2026-02-01T00:00:00Z'");

    // Act / Assert
    assertAll(
        () -> probe.expectViolation(activeWithoutInstant, "ck_customer_active_iff_activated"),
        () -> probe.expectViolation(onboardingWithInstant, "ck_customer_active_iff_activated"));
  }

  /** A negative interval corrupts the periodic-review anniversary and the AML retention anchor. */
  @Test
  void activation_before_registration_is_rejected() {
    // Arrange
    String sql =
        corporate(
            UUID.randomUUID(),
            "0000000067",
            "a3@example.com",
            "'ACTIVE'",
            "'2025-01-01T00:00:00Z'");

    // Act / Assert
    probe.expectViolation(sql, "ck_customer_activation_not_before_registration");
  }

  @Test
  void the_subtype_discriminant_rejects_a_row_that_mixes_both_shapes() {
    // Arrange
    String individualWithCorporateColumn =
        """
        INSERT INTO customer.customer
          (id, customer_number, email, status, registered_at, kind,
           given_name, family_name, date_of_birth, gender, country_of_residence, registered_name)
        VALUES ('%s', '0000000075', 'm1@example.com', 'ONBOARDING', '2026-01-01T00:00:00Z',
                'INDIVIDUAL', 'A', 'B', '1990-01-01', 'MALE', 'FR', 'Acme SA')
        """
            .formatted(UUID.randomUUID());
    String corporateWithMiddleName =
        """
        INSERT INTO customer.customer
          (id, customer_number, email, status, registered_at, kind,
           middle_name, registered_name, registration_number, country_of_incorporation)
        VALUES ('%s', '0000000083', 'm2@example.com', 'ONBOARDING', '2026-01-01T00:00:00Z',
                'CORPORATE', 'Mary', 'Acme SA', 'RCS-1', 'FR')
        """
            .formatted(UUID.randomUUID());

    // Act / Assert
    assertAll(
        () -> probe.expectViolation(individualWithCorporateColumn, "ck_customer_subtype_columns"),
        () -> probe.expectViolation(corporateWithMiddleName, "ck_customer_subtype_columns"));
  }

  /**
   * Without these, a bad write surfaces at read time as {@code valueOf} blowing up inside {@code
   * reconstitute} — on the read path, far from the cause.
   */
  @Test
  void enumerated_columns_reject_a_value_outside_their_enum() {
    // Arrange
    String unknownKind =
        corporateWithKind(UUID.randomUUID(), "0000000091", "e1@example.com", "TRUST");
    String unknownStatus =
        corporate(UUID.randomUUID(), "0000000109", "e2@example.com", "'SUSPENDED'", "NULL");

    // Act / Assert
    assertAll(
        () -> probe.expectViolation(unknownKind, "ck_customer_kind_known"),
        () -> probe.expectViolation(unknownStatus, "ck_customer_status_known"));
  }

  /**
   * US-person status is a set-membership question, so a lower-case row would silently fail to match
   * the one check that is legally non-negotiable.
   */
  @Test
  void a_country_code_stored_in_lower_case_is_rejected() {
    // Arrange
    String sql =
        """
        INSERT INTO customer.customer
          (id, customer_number, email, status, registered_at, kind,
           registered_name, registration_number, country_of_incorporation)
        VALUES ('%s', '0000000117', 'c1@example.com', 'ONBOARDING', '2026-01-01T00:00:00Z',
                'CORPORATE', 'Acme SA', 'RCS-1', 'us')
        """
            .formatted(UUID.randomUUID());

    // Act / Assert
    probe.expectViolation(sql, "ck_customer_incorporation_shape");
  }

  /**
   * A plain {@code UNIQUE} would let {@code ada@example.com} and {@code Ada@Example.com} coexist,
   * which is two CDD records for one person.
   */
  @Test
  void an_email_that_is_not_canonically_lower_case_is_rejected() {
    // Arrange
    String sql = corporate(UUID.randomUUID(), "0000000125", "Ada@Example.com");

    // Act / Assert
    probe.expectViolation(sql, "ck_customer_email_canonical");
  }

  /** A blank name is a customer record with no name, and it fails on read rather than on write. */
  @Test
  void blank_required_text_is_rejected() {
    // Arrange
    String blankGivenName =
        """
        INSERT INTO customer.customer
          (id, customer_number, email, status, registered_at, kind,
           given_name, family_name, date_of_birth, gender, country_of_residence)
        VALUES ('%s', '0000000133', 'b1@example.com', 'ONBOARDING', '2026-01-01T00:00:00Z',
                'INDIVIDUAL', '   ', 'Lovelace', '1990-01-01', 'FEMALE', 'FR')
        """
            .formatted(UUID.randomUUID());
    String blankRegisteredName =
        """
        INSERT INTO customer.customer
          (id, customer_number, email, status, registered_at, kind,
           registered_name, registration_number, country_of_incorporation)
        VALUES ('%s', '0000000141', 'b2@example.com', 'ONBOARDING', '2026-01-01T00:00:00Z',
                'CORPORATE', '', 'RCS-1', 'FR')
        """
            .formatted(UUID.randomUUID());

    // Act / Assert
    assertAll(
        () -> probe.expectViolation(blankGivenName, "ck_customer_required_text_not_blank"),
        () -> probe.expectViolation(blankRegisteredName, "ck_customer_required_text_not_blank"));
  }

  /**
   * A static bound only. Not-future and minimum age are time-varying and belong in the use case,
   * which does not exist yet — so at this moment this is the only thing bounding an AML input at
   * all.
   */
  @Test
  void an_absurd_date_of_birth_is_rejected() {
    // Arrange
    UUID id = UUID.randomUUID();
    String sql =
        """
        INSERT INTO customer.customer
          (id, customer_number, email, status, registered_at, kind,
           given_name, family_name, date_of_birth, gender, country_of_residence)
        VALUES ('%s', '0000000158', 'd1@example.com', 'ONBOARDING', '2026-01-01T00:00:00Z',
                'INDIVIDUAL', 'Ada', 'Lovelace', '2300-01-01', 'FEMALE', 'FR')
        """
            .formatted(id);

    // Act / Assert
    probe.expectViolation(sql, "ck_customer_date_of_birth_plausible");
  }

  /**
   * The persistence adapter has to translate these two differently — a duplicate email is a
   * client-fixable conflict, a duplicate number is a server-side collision in number issuance — so
   * the constraint names are part of the contract rather than an implementation detail.
   */
  @Test
  void an_email_conflict_and_a_number_conflict_are_reported_by_distinct_constraints() {
    // Arrange
    String duplicateEmail = corporate(UUID.randomUUID(), "0000000166", "ada@example.com");
    String duplicateNumber = corporate(UUID.randomUUID(), "0000000018", "n1@example.com");

    // Act / Assert
    assertAll(
        () -> probe.expectViolation(duplicateEmail, "uq_customer_email"),
        () -> probe.expectViolation(duplicateNumber, "uq_customer_number"));
  }

  /**
   * The composite foreign key and the {@code CHECK} are both defeated at once by a NULL {@code
   * kind}, which is why the column is {@code NOT NULL}: {@code MATCH SIMPLE} skips the key when any
   * referencing column is NULL, and a {@code CHECK} passes on NULL. Without the third assertion a
   * corporate could acquire nationality rows and orphans would be insertable — contaminating
   * exactly the query this table exists for.
   */
  @Test
  void nationalities_are_confined_to_individuals_that_exist() {
    // Arrange
    String onACorporate =
        "INSERT INTO customer.customer_nationality VALUES ('%s', 'CORPORATE', 'FR')"
            .formatted(CORPORATE_ID);
    String withoutAKind =
        "INSERT INTO customer.customer_nationality VALUES ('%s', NULL, 'FR')"
            .formatted(CORPORATE_ID);
    String orphan =
        "INSERT INTO customer.customer_nationality VALUES ('%s', 'INDIVIDUAL', 'DE')"
            .formatted(UUID.randomUUID());

    // Act / Assert
    assertAll(
        () -> probe.expectViolation(onACorporate, "ck_customer_nationality_individuals_only"),
        () -> probe.expectNotNullViolation(withoutAKind, "kind"),
        () -> probe.expectViolation(orphan, "fk_customer_nationality_customer"));
  }

  /**
   * Both bounds of the {@code Nationalities} value object, enforced at rest by a deferred
   * constraint trigger because neither is a single-row property.
   *
   * <p>The lower bound is the one that matters: an individual holding none is not merely
   * incomplete, it is unrehydratable, because every read of it constructs {@code Nationalities} and
   * that throws.
   */
  @Test
  void an_individual_must_hold_between_one_and_ten_nationalities() {
    // Arrange
    UUID none = UUID.randomUUID();
    UUID eleven = UUID.randomUUID();
    String[] tooMany =
        IntStream.range(0, 11)
            .mapToObj(i -> nationality(eleven.toString(), TEN_PLUS_ONE_COUNTRIES[i]))
            .toArray(String[]::new);

    // Act / Assert
    assertAll(
        () ->
            probe.expectRaisedRejectionInTransaction(
                "must hold between 1 and 10 nationalities",
                individual(none, "0000000174", "z1@example.com", null)),
        () ->
            probe.expectRaisedRejectionInTransaction(
                "must hold between 1 and 10 nationalities",
                prepend(individual(eleven, "0000000182", "z2@example.com", null), tooMany)));
  }

  /**
   * One UPDATE moving a nationality row between customers touches two sets, and the customer it was
   * taken FROM is the one a naive guard never checks. Verified: before the trigger looked at both
   * the old and the new parent, this single statement stripped an individual's last nationality and
   * committed -- the same end state as a DELETE, which was already refused, so the guard's own
   * comment promised more than it delivered.
   */
  @Test
  void an_individuals_last_nationality_cannot_be_moved_to_another_customer() {
    // Arrange
    UUID donor = UUID.randomUUID();
    UUID recipient = UUID.randomUUID();
    probe.executeAll(
        individual(donor, "0000000224", "donor@example.com", null),
        nationality(donor.toString(), "FR"));
    probe.executeAll(
        individual(recipient, "0000000232", "recipient@example.com", null),
        nationality(recipient.toString(), "DE"));

    // Act / Assert
    // The donor id, not just the message shape: the trigger names which customer was left short,
    // and
    // asserting it is what makes this test pin its own name rather than "somebody was refused".
    probe.expectRaisedRejectionInTransaction(
        "individual customer %s must hold between 1 and 10 nationalities".formatted(donor),
        """
        UPDATE customer.customer_nationality SET customer_id = '%s'
         WHERE customer_id = '%s' AND country_code = 'FR'
        """
            .formatted(recipient, donor));
  }

  /**
   * The monotonicity trigger is BEFORE UPDATE, so on its own it is defeated without ever performing
   * an update: delete the singleton and re-insert at version 1. There is no legitimate delete here
   * -- the row exists for the lifetime of the schema -- so both removal paths are guarded.
   */
  @Test
  void the_policy_version_cannot_be_reset_by_removing_the_row() {
    // Act / Assert
    assertAll(
        () ->
            probe.expectRaisedRejection(
                "DELETE FROM customer.admission_policy", "may not be deleted by the application"),
        () ->
            probe.expectRaisedRejection(
                "TRUNCATE customer.admission_policy", "may not be deleted by the application"));
  }

  /**
   * Pins the row lock the cardinality trigger takes on the parent before counting, which is what
   * keeps the lower bound true under concurrency: without it, two transactions each removing one of
   * a customer's two nationalities can both count 1 -- each seeing the other's row still present --
   * and the customer commits holding none.
   *
   * <p>Asserted through contention rather than by racing two deletes, because racing them proves
   * nothing: at READ COMMITTED the second transaction's count re-snapshots and refuses regardless.
   * That version of this test was written, passed with the lock removed, and was replaced.
   */
  @Test
  void a_nationality_change_contends_for_the_customer_row() {
    // Arrange
    UUID id = UUID.randomUUID();
    probe.executeAll(
        individual(id, "0000000216", "race@example.com", null),
        nationality(id.toString(), "FR"),
        nationality(id.toString(), "DE"));

    // Act / Assert
    probe.expectBlockedWhileCustomerLocked(id.toString(), deleteNationality(id, "FR"));
  }

  /**
   * The deliberate asymmetry with the audit tables, which are {@code RESTRICT}: nationalities are
   * part of the customer's state and die with it, whereas audit rows are evidence about the
   * customer and must outlive it.
   *
   * <p>Reached through the sanctioned maintenance path, because deleting a customer is a privileged
   * purge operation rather than something the application may do.
   */
  @Test
  void deleting_a_customer_takes_its_nationalities_with_it() {
    // Arrange
    UUID id = UUID.randomUUID();
    probe.executeAll(
        individual(id, "0000000190", "cascade@example.com", null),
        nationality(id.toString(), "FR"));

    // Act
    probe.executeWithGuardSuspended(
        "customer",
        "trg_customer_no_delete",
        "DELETE FROM customer.customer WHERE id = '%s'".formatted(id));

    // Assert
    assertThat(
            probe.count(
                "SELECT count(*) FROM customer.customer_nationality WHERE customer_id = '%s'"
                    .formatted(id)))
        .isZero();
  }

  /**
   * Polarity is structural: the deny side fails open when a row is missing, the allow side fails
   * closed. Crossing the two tables is the mistake that silently inverts a rule, so each rejects
   * the other's values.
   */
  @Test
  void the_two_policy_tables_cannot_be_crossed() {
    // Arrange
    String unlicensedOnTheDenySide =
        "INSERT INTO customer.restricted_country VALUES ('BR', 'UNLICENSED', 'RESIDENCE')";
    String nationalityOnTheAllowSide =
        "INSERT INTO customer.licensed_country VALUES ('FR', 'NATIONALITY')";

    // Act / Assert
    assertAll(
        () ->
            probe.expectViolation(
                unlicensedOnTheDenySide, "ck_restricted_country_restriction_known"),
        () -> probe.expectViolation(nationalityOnTheAllowSide, "ck_licensed_country_factor_known"));
  }

  @Test
  void the_admission_policy_version_is_a_single_row() {
    // Arrange
    String secondRow = "INSERT INTO customer.admission_policy (version) VALUES (2)";

    // Act / Assert
    assertAll(
        () ->
            assertThat(probe.count("SELECT count(*) FROM customer.admission_policy")).isEqualTo(1),
        () -> probe.expectViolation(secondRow, "pk_admission_policy"));
  }

  /**
   * A reused version number would describe two different rule sets, making every decision row
   * citing it ambiguous. {@code CHECK} cannot see the previous value, so this needs a trigger.
   */
  @Test
  void the_admission_policy_version_cannot_be_walked_backwards() {
    // Arrange
    long current = probe.count("SELECT version FROM customer.admission_policy");

    // Act / Assert
    assertAll(
        () ->
            probe.expectRaisedRejectionInTransaction(
                "admission policy version must advance",
                "UPDATE customer.admission_policy SET version = %d".formatted(current - 1)),
        () ->
            probe.expectRaisedRejectionInTransaction(
                "admission policy version must advance",
                "UPDATE customer.admission_policy SET version = %d".formatted(current)));
  }

  /**
   * The factor-level startup assertion planned for this schema cannot detect the loss of any single
   * row, and these three are the whole of the US-person rule the admission design turns on. So they
   * are named here explicitly.
   */
  @Test
  void the_us_person_rule_is_seeded_as_three_rows() {
    // Arrange / Act
    long seeded =
        probe.count(
            """
            SELECT count(*) FROM customer.restricted_country
             WHERE country_code = 'US' AND restriction = 'RESTRICTED_PERSON'
               AND connecting_factor IN ('NATIONALITY', 'RESIDENCE', 'INCORPORATION')
            """);

    // Assert
    assertThat(seeded)
        .as("the FATCA nationality limb, the residence limb and the entity limb must all be seeded")
        .isEqualTo(3);
  }

  /**
   * A tripwire, not a permanent expectation. Every licensing row depends on the home authorisation
   * jurisdiction, which is recorded nowhere in this repository, so seeding this table today would
   * put unvalidated rows behind an allow-list whose failure mode is a regulatory breach. When the
   * jurisdiction is named, this assertion is what forces the seed to be a deliberate act.
   */
  @Test
  void licensed_country_is_deliberately_unseeded() {
    // Arrange / Act
    long rows = probe.count("SELECT count(*) FROM customer.licensed_country");

    // Assert
    assertThat(rows)
        .as(
            "seeding licensing rules requires the home authorisation jurisdiction to be recorded"
                + " first; if it now is, replace this assertion with one over the seeded rows")
        .isZero();
  }

  @Test
  void an_admission_decision_must_describe_an_evaluation_that_could_have_happened() {
    // Arrange
    String individualCarryingIncorporation =
        """
        INSERT INTO customer.admission_decision
          (id, idempotency_key, policy_version, outcome, subject_type,
           nationalities, country_of_residence, country_of_incorporation, decided_at)
        VALUES ('%s', 'k1', 1, 'REFUSED', 'INDIVIDUAL',
                ARRAY['FR']::varchar(2)[], 'FR', 'FR', '2026-01-01T00:00:00Z')
        """
            .formatted(UUID.randomUUID());
    // array_to_string skips NULL elements, so the regex alone renders this as 'FR' and accepts it.
    String nationalitiesHoldingANull =
        """
        INSERT INTO customer.admission_decision
          (id, idempotency_key, policy_version, outcome, subject_type,
           nationalities, country_of_residence, decided_at)
        VALUES ('%s', 'k2', 1, 'REFUSED', 'INDIVIDUAL',
                ARRAY['FR', NULL]::varchar(2)[], 'FR', '2026-01-01T00:00:00Z')
        """
            .formatted(UUID.randomUUID());

    // Act / Assert
    assertAll(
        () ->
            probe.expectViolation(
                individualCarryingIncorporation, "ck_admission_decision_subject_columns"),
        () ->
            probe.expectViolation(
                nationalitiesHoldingANull, "ck_admission_decision_nationalities_shape"));
  }

  /**
   * Under the tipping-off rule the client response is deliberately uninformative, so these rows are
   * the only record of why someone was declined. A refusal carrying none is a refusal with no
   * reason.
   */
  @Test
  void a_refusal_must_record_at_least_one_match() {
    // Arrange
    UUID decisionId = UUID.randomUUID();

    // Act / Assert
    probe.expectRaisedRejectionInTransaction(
        "is REFUSED but records no match", decision(decisionId, "r1", "REFUSED"));
  }

  /**
   * "We found a sanctions hit and onboarded them anyway" is the worst state this schema can hold.
   */
  @Test
  void an_admitted_decision_must_record_no_match() {
    // Arrange
    UUID decisionId = UUID.randomUUID();

    // Act / Assert
    probe.expectRaisedRejectionInTransaction(
        "is ADMITTED but records",
        decision(decisionId, "r2", "ADMITTED"),
        match(decisionId, 0, "SANCTIONED", "INCORPORATION", "RU"));
  }

  /**
   * The primary-match index is partial on {@code ordinal = 0}, so a set of matches starting at 1
   * drops out of the "sanctions refusals in this period" answer — returning a confidently wrong
   * number, which is worse than an error.
   */
  @Test
  void match_ordinals_must_be_contiguous_from_zero() {
    // Arrange
    UUID decisionId = UUID.randomUUID();

    // Act / Assert
    probe.expectRaisedRejectionInTransaction(
        "ordinals must be contiguous from 0",
        decision(decisionId, "r3", "REFUSED"),
        match(decisionId, 1, "SANCTIONED", "INCORPORATION", "RU"));
  }

  /**
   * A customer may legitimately hold several decisions — two concurrent requests sharing an
   * idempotency key each commit their own — but a decision belongs to at most one customer, and
   * that is the direction the retention anchor depends on.
   */
  @Test
  void a_decision_anchors_to_at_most_one_customer() {
    // Arrange
    UUID decisionId = UUID.randomUUID();
    probe.execute(decision(decisionId, "k3", "ADMITTED"));
    probe.execute(link(CORPORATE_ID, decisionId));

    // Act / Assert
    // A second CORPORATE customer, because subject_type is now keyed against customer.kind —
    // linking to
    // an individual would fail on the wrong constraint and leave the cardinality rule untested.
    UUID otherCorporate = UUID.randomUUID();
    probe.execute(corporate(otherCorporate, "0000000240", "other@example.com"));
    probe.expectViolation(
        link(otherCorporate.toString(), decisionId), "uq_customer_admission_decision");
  }

  /**
   * Only an admitted decision is CDD evidence for a customer, so only an admitted decision may be
   * anchored to one. Enforced rather than asserted: an earlier version of this table carried two
   * independent foreign keys, so a REFUSED decision could be linked — and the audit test's own
   * fixture did exactly that and committed, which is how the gap was found.
   *
   * <p>It matters beyond tidiness. A linked refusal reads as an onboarded customer to anyone
   * joining these tables, and it moves that record from the decided_at retention clock onto the
   * relationship-end one.
   */
  @Test
  void a_refused_decision_cannot_be_anchored_to_a_customer() {
    // Arrange
    UUID refused = UUID.randomUUID();
    probe.executeAll(
        decision(refused, "refused-link", "REFUSED"),
        match(refused, 0, "SANCTIONED", "INCORPORATION", "RU"));

    // Act / Assert
    assertAll(
        // Claiming ADMITTED on the link is caught by the composite key, which makes the copy agree
        // with
        // the decision it names.
        () ->
            probe.expectViolation(
                link(CORPORATE_ID, refused, "ADMITTED", "CORPORATE"),
                "fk_customer_admission_decision"),
        // Telling the truth about the outcome is caught by the CHECK.
        () ->
            probe.expectViolation(
                link(CORPORATE_ID, refused, "REFUSED", "CORPORATE"),
                "ck_customer_admission_admitted_only"));
  }

  /**
   * The decision's subject type and the customer's kind are keyed against each other, so a
   * corporate screening decision cannot be recorded as the evidence for an individual. Without this
   * the link table would happily assert that a company's admission check onboarded a person.
   */
  @Test
  void a_decision_cannot_be_anchored_to_a_customer_of_another_type() {
    // Arrange
    UUID corporateDecision = UUID.randomUUID();
    probe.execute(decision(corporateDecision, "type-mismatch", "ADMITTED"));

    // Act / Assert
    assertAll(
        // Naming the customer's kind breaks the key to the decision...
        () ->
            probe.expectViolation(
                link(INDIVIDUAL_ID, corporateDecision, "ADMITTED", "INDIVIDUAL"),
                "fk_customer_admission_decision"),
        // ...and naming the decision's subject type breaks the key to the customer.
        () ->
            probe.expectViolation(
                link(INDIVIDUAL_ID, corporateDecision, "ADMITTED", "CORPORATE"),
                "fk_customer_admission_customer"));
  }

  /**
   * Asserted from the catalog rather than by exhausting the sequence: for a sequence the
   * declaration <em>is</em> the behaviour, and {@code nextval} is not transactional, so a
   * behavioural test would leave the sequence permanently advanced for every later test.
   *
   * <p>Both bounds are load-bearing. Starting above zero is half the guarantee that the
   * number-generation cycle walk is a bijection, and the ceiling is where the body would gain a
   * tenth digit.
   */
  @Test
  void the_customer_number_sequence_is_bounded_to_a_nine_digit_body() {
    // Arrange / Act
    long startValue = sequenceAttribute("start_value");
    long maxValue = sequenceAttribute("max_value");
    long cycling =
        probe.count(
            "SELECT count(*) FROM pg_sequences"
                + " WHERE schemaname = 'customer' AND sequencename = 'customer_number_seq'"
                + " AND cycle");

    // Assert
    assertAll(
        () -> assertThat(startValue).as("zero must be unreachable").isEqualTo(1L),
        () -> assertThat(maxValue).as("a tenth digit must be unreachable").isEqualTo(999_999_999L),
        () -> assertThat(cycling).as("exhaustion must raise, not reissue").isZero());
  }

  private long sequenceAttribute(String column) {
    return probe.count(
        "SELECT %s FROM pg_sequences".formatted(column)
            + " WHERE schemaname = 'customer' AND sequencename = 'customer_number_seq'");
  }

  private static final String[] TEN_PLUS_ONE_COUNTRIES = {
    "FR", "GB", "DE", "IT", "ES", "PT", "BE", "NL", "SE", "NO", "DK"
  };

  private static String[] prepend(String first, String[] rest) {
    String[] all = new String[rest.length + 1];
    all[0] = first;
    System.arraycopy(rest, 0, all, 1, rest.length);
    return all;
  }

  private static String deleteNationality(UUID customerId, String countryCode) {
    return "DELETE FROM customer.customer_nationality WHERE customer_id = '%s' AND country_code = '%s'"
        .formatted(customerId, countryCode);
  }

  /** Idempotent like the customer fixture, since setUp runs per test and these rows persist. */
  private static String nationality(String customerId, String countryCode) {
    return """
        INSERT INTO customer.customer_nationality VALUES ('%s', 'INDIVIDUAL', '%s')
        ON CONFLICT DO NOTHING
        """
        .formatted(customerId, countryCode);
  }

  private static String decision(UUID id, String key, String outcome) {
    return """
        INSERT INTO customer.admission_decision
          (id, idempotency_key, policy_version, outcome, subject_type,
           country_of_incorporation, decided_at)
        VALUES ('%s', '%s', 1, '%s', 'CORPORATE', 'FR', '2026-01-01T00:00:00Z')
        """
        .formatted(id, key, outcome);
  }

  private static String match(
      UUID decisionId, int ordinal, String restriction, String factor, String country) {
    return """
        INSERT INTO customer.admission_decision_match
          (decision_id, ordinal, restriction, connecting_factor, triggering_country)
        VALUES ('%s', %d, '%s', '%s', '%s')
        """
        .formatted(decisionId, ordinal, restriction, factor, country);
  }

  private static String link(String customerId, UUID decisionId) {
    return link(customerId, decisionId, "ADMITTED", "CORPORATE");
  }

  private static String link(
      String customerId, UUID decisionId, String outcome, String subjectType) {
    return """
        INSERT INTO customer.customer_admission (customer_id, decision_id, outcome, subject_type)
        VALUES ('%s', '%s', '%s', '%s')
        """
        .formatted(customerId, decisionId, outcome, subjectType);
  }

  private static String individual(UUID id, String number, String email, String middleName) {
    return """
        INSERT INTO customer.customer
          (id, customer_number, email, status, registered_at, kind,
           given_name, middle_name, family_name, date_of_birth, gender, country_of_residence)
        VALUES ('%s', '%s', '%s', 'ONBOARDING', '2026-01-01T00:00:00Z', 'INDIVIDUAL',
                'Alan', %s, 'Turing', '1912-06-23', 'MALE', 'GB')
        """
        .formatted(id, number, email, middleName == null ? "NULL" : "'" + middleName + "'");
  }

  private static String corporate(UUID id, String number, String email) {
    return corporate(id, number, email, "'ONBOARDING'", "NULL");
  }

  private static String corporate(
      UUID id, String number, String email, String status, String activatedAt) {
    return corporate(id, number, email, status, activatedAt, "CORPORATE");
  }

  private static String corporateWithKind(UUID id, String number, String email, String kind) {
    return corporate(id, number, email, "'ONBOARDING'", "NULL", kind);
  }

  private static String corporate(
      UUID id, String number, String email, String status, String activatedAt, String kind) {
    return """
        INSERT INTO customer.customer
          (id, customer_number, email, status, registered_at, activated_at, kind,
           registered_name, registration_number, country_of_incorporation)
        VALUES ('%s', '%s', '%s', %s, '2026-01-01T00:00:00Z', %s, '%s',
                'Acme SA', 'RCS-1', 'FR')
        """
        .formatted(id, number, email, status, activatedAt, kind);
  }
}
