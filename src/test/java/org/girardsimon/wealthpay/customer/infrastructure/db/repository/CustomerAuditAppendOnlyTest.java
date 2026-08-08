package org.girardsimon.wealthpay.customer.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;

/**
 * Append-only on the audit tables is a claim about what the <em>owning superuser</em> cannot do,
 * since that is what the application, Flyway and Debezium all connect as. Privileges therefore
 * prove nothing here, and neither does the presence of a trigger: the default enable state is
 * bypassable by a single {@code SET}, and the difference is invisible in a green migration. So the
 * guarantee is asserted behaviourally, against each documented bypass.
 *
 * <p>Every case below is a path verified to defeat a weaker design: a plain {@code REVOKE}
 * (defeated by ownership), {@code ON DELETE CASCADE} (defeated because referential actions run as
 * the table owner), {@code TRUNCATE} (row triggers do not fire), and {@code
 * session_replication_role = replica} (defeats triggers and referential actions together unless
 * they are {@code ENABLE ALWAYS}).
 */
@JooqTest
class CustomerAuditAppendOnlyTest extends AbstractCustomerContainerTest {

  private static final String CUSTOMER_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
  private static final String REFUSED_DECISION_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
  private static final String ADMITTED_DECISION_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

  @Autowired private DataSource dataSource;

  private SchemaProbe probe;

  /**
   * A row per table is required, not incidental: a row-level {@code BEFORE UPDATE} trigger does not
   * fire on an empty table, so an unguarded table would pass an update test that found nothing to
   * update.
   */
  @BeforeEach
  void setUp() {
    probe = new SchemaProbe(dataSource);
    // One transaction, because the decision and its match rows are only consistent together: a
    // REFUSED
    // decision with no match yet is a state the deferred constraint trigger correctly rejects.
    probe.executeAll(
        """
        INSERT INTO customer.customer
          (id, customer_number, email, status, registered_at, activated_at, kind,
           registered_name, registration_number, country_of_incorporation)
        VALUES ('%s', '0000000208', 'audit@example.com', 'ACTIVE', '2026-01-01T00:00:00Z',
                '2026-02-01T00:00:00Z', 'CORPORATE', 'Audited SA', 'RCS-9', 'FR')
        ON CONFLICT DO NOTHING
        """
            .formatted(CUSTOMER_ID),
        """
        INSERT INTO customer.customer_status_transition
          (customer_id, sequence_no, from_status, to_status, occurred_at, actor)
        VALUES ('%s', 1, 'ONBOARDING', 'ACTIVE', '2026-02-01T00:00:00Z', 'SYSTEM')
        ON CONFLICT DO NOTHING
        """
            .formatted(CUSTOMER_ID),
        """
        INSERT INTO customer.admission_decision
          (id, idempotency_key, policy_version, outcome, subject_type,
           country_of_incorporation, decided_at)
        VALUES ('%s', 'audit-refused', 1, 'REFUSED', 'CORPORATE', 'FR', '2026-01-01T00:00:00Z')
        ON CONFLICT DO NOTHING
        """
            .formatted(REFUSED_DECISION_ID),
        """
        INSERT INTO customer.admission_decision_match
          (decision_id, ordinal, restriction, connecting_factor, triggering_country)
        VALUES ('%s', 0, 'RESTRICTED_PERSON', 'INCORPORATION', 'US')
        ON CONFLICT DO NOTHING
        """
            .formatted(REFUSED_DECISION_ID),
        // A SECOND, admitted decision, because only an admitted one may be anchored to a customer.
        // The
        // first version of this fixture linked the refused decision above -- and that it committed
        // was
        // the tell that the schema was not enforcing what its comment claimed.
        """
        INSERT INTO customer.admission_decision
          (id, idempotency_key, policy_version, outcome, subject_type,
           country_of_incorporation, decided_at)
        VALUES ('%s', 'audit-admitted', 1, 'ADMITTED', 'CORPORATE', 'FR', '2026-01-01T00:00:00Z')
        ON CONFLICT DO NOTHING
        """
            .formatted(ADMITTED_DECISION_ID),
        """
        INSERT INTO customer.customer_admission (customer_id, decision_id, outcome, subject_type)
        VALUES ('%s', '%s', 'ADMITTED', 'CORPORATE')
        ON CONFLICT DO NOTHING
        """
            .formatted(CUSTOMER_ID, ADMITTED_DECISION_ID));
  }

  /** Assigning a column to itself is enough: a {@code BEFORE UPDATE} trigger fires on any match. */
  @ParameterizedTest
  @CsvSource({
    "customer_status_transition, actor",
    "admission_decision, outcome",
    "admission_decision_match, restriction",
    "customer_admission, linked_at"
  })
  void an_audit_row_cannot_be_updated(String table, String column) {
    // Arrange
    String sql = "UPDATE customer.%s SET %s = %s".formatted(table, column, column);

    // Act / Assert
    probe.expectRaisedRejection(sql, "is append-only");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "customer_status_transition",
        "admission_decision",
        "admission_decision_match",
        "customer_admission"
      })
  void an_audit_row_cannot_be_deleted(String table) {
    // Arrange
    String sql = "DELETE FROM customer.%s".formatted(table);

    // Act / Assert
    probe.expectRaisedRejection(sql, "is append-only");
  }

  /**
   * {@code CASCADE} rather than a plain {@code TRUNCATE}, for two reasons: it is the form ADR-009
   * names as the bypass, and on a referenced table a plain {@code TRUNCATE} is rejected by the
   * foreign key check <em>before</em> the trigger runs, so it would never reach the guard under
   * test.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "customer_status_transition",
        "admission_decision",
        "admission_decision_match",
        "customer_admission"
      })
  void an_audit_table_cannot_be_truncated(String table) {
    // Arrange
    String sql = "TRUNCATE customer.%s CASCADE".formatted(table);

    // Act / Assert
    probe.expectRaisedRejection(sql, "is append-only");
  }

  /**
   * The case that makes {@code ENABLE ALWAYS} the requirement rather than a preference. One {@code
   * SET} disables ordinary triggers and referential actions together, and it is available to this
   * application because it runs as a superuser.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "customer_status_transition",
        "admission_decision",
        "admission_decision_match",
        "customer_admission"
      })
  void replica_session_role_does_not_bypass_the_guard(String table) {
    // Arrange
    String sql = "DELETE FROM customer.%s".formatted(table);

    // Act / Assert
    probe.expectRaisedRejectionUnderReplicaRole(sql, "is append-only");
  }

  /**
   * The guards must all be present and all {@code ENABLE ALWAYS}, asserted as state rather than
   * only as per-operation behaviour. A disabled guard fails nothing — it quietly downgrades later
   * assertions from "the trigger refused this" to "some other constraint refused this", which is
   * how a leaked {@code DISABLE TRIGGER} in a test helper first surfaced.
   *
   * <p>17 = 4 audit tables x (UPDATE, DELETE, TRUNCATE), plus DELETE and TRUNCATE on customer, plus
   * TRUNCATE on customer_nationality, plus DELETE and TRUNCATE on admission_policy.
   */
  @Test
  void every_write_guard_is_present_and_enabled_always() {
    // Arrange / Act
    long guards =
        probe.count(
            """
            SELECT count(*) FROM pg_trigger
             WHERE NOT tgisinternal
               AND tgenabled = 'A'
               AND tgfoid IN ('customer.audit_append_only'::regproc,
                              'customer.deletion_is_privileged'::regproc)
            """);

    // Assert
    assertThat(guards)
        .as("every write guard must be ENABLE ALWAYS, not merely present")
        .isEqualTo(17);
  }

  /**
   * The complement of the count above, and the assertion that closes this class of defect: NO
   * trigger in the schema, of any kind, may sit at its default enable state.
   *
   * <p>Counting what is {@code ENABLE ALWAYS} cannot notice what is not, and the parameterised
   * replica-role tests below cover a hand-written list of four table names. Between them they
   * missed four deferred correctness triggers that one {@code SET} could silence — including the
   * one that forbids an ADMITTED decision carrying a sanctions match. This query is exhaustive over
   * the catalog instead, so every trigger a future migration adds is covered the day it lands
   * rather than when someone remembers to extend a list.
   */
  @Test
  void no_trigger_in_the_schema_can_be_silenced_by_replica_role() {
    // Arrange / Act
    long silenceable =
        probe.count(
            """
            SELECT count(*) FROM pg_trigger t
              JOIN pg_class c ON c.oid = t.tgrelid
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'customer' AND NOT t.tgisinternal AND t.tgenabled <> 'A'
            """);

    // Assert
    assertThat(silenceable)
        .as(
            "every trigger in the customer schema must be ENABLE ALWAYS, guards and constraints alike")
        .isZero();
  }

  /**
   * The nationality child needs the TRUNCATE half of the guard, and it is the only half it needs.
   *
   * <p>This is the same bypass the audit tables are guarded against, applied to a table that is not
   * append-only: row triggers do not fire on TRUNCATE, and the deferred cardinality trigger that
   * refuses {@code DELETE FROM customer.customer_nationality} is a row trigger. So a direct
   * TRUNCATE emptied the table, fired nothing, left every customer row in place, and made every
   * individual unrehydratable while the FATCA-by-country query silently returned nothing. No race
   * and no privilege needed — one statement.
   */
  @Test
  void the_nationality_child_cannot_be_truncated() {
    // Arrange
    String sql = "TRUNCATE customer.customer_nationality";

    // Act / Assert
    probe.expectRaisedRejection(sql, "may not be deleted by the application");
  }

  /**
   * Deleting a customer is a privileged purge operation, so the application cannot reach it at all.
   */
  @Test
  void a_customer_cannot_be_deleted_by_the_application() {
    // Arrange
    String sql = "DELETE FROM customer.customer WHERE id = '%s'".formatted(CUSTOMER_ID);

    // Act / Assert
    probe.expectRaisedRejection(sql, "may not be deleted by the application");
  }

  /**
   * The second layer, and the reason the foreign keys are {@code RESTRICT} rather than {@code
   * CASCADE} even now that a trigger guards the parent. Asserted with the parent guard suspended,
   * because that is the only state in which a delete gets far enough to consult a referential
   * action — and it is exactly the state the retention purge will run in.
   *
   * <p>Referential actions are not privilege-checked against the caller: PostgreSQL runs them as
   * the table owner, so a {@code CASCADE} here would delete audit rows the caller is explicitly
   * forbidden to delete, with no error.
   */
  @Test
  void even_with_the_parent_guard_suspended_the_audit_trail_holds() {
    // Arrange
    String suspend = SchemaProbe.disableTrigger("customer", "trg_customer_no_delete");
    String delete = "DELETE FROM customer.customer WHERE id = '%s'".formatted(CUSTOMER_ID);

    // Act / Assert
    probe.expectViolationInTransaction("fk_customer_status_transition_customer", suspend, delete);
  }

  /**
   * The escape hatch has to work, or the retention purge that D4 makes mandatory has no way in.
   * Rolled back, so this asserts the path is open without using it.
   */
  @Test
  void the_sanctioned_maintenance_path_can_still_delete() {
    // Arrange
    String triggerName = "trg_customer_status_transition_append_only_delete";

    // Act
    long deletable = probe.deletableWithGuardSuspended("customer_status_transition", triggerName);

    // Assert
    assertThat(deletable)
        .as("ALTER TABLE ... DISABLE TRIGGER must still work against an ENABLE ALWAYS trigger")
        .isPositive();
  }

  /**
   * The guard must come back on its own. If suspending it leaked past the transaction, the purge
   * job would silently leave every audit table writable.
   */
  @Test
  void suspending_the_guard_does_not_outlive_the_transaction() {
    // Arrange
    probe.deletableWithGuardSuspended(
        "customer_status_transition", "trg_customer_status_transition_append_only_delete");

    // Act / Assert
    probe.expectRaisedRejection(
        "DELETE FROM customer.customer_status_transition", "is append-only");
  }
}
