package org.girardsimon.wealthpay.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.girardsimon.wealthpay.account.infrastructure.db.AccountFlywayConfig;
import org.girardsimon.wealthpay.customer.infrastructure.db.CustomerFlywayConfig;
import org.girardsimon.wealthpay.testsupport.AbstractContainerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The only test that puts <em>both</em> bounded contexts' Flyway beans in one context, which is the
 * arrangement production runs and no other test reproduces: every container test imports a single
 * bounded context's configuration, by design, so that a broken migration fails only its own suite.
 *
 * <p>It is deliberately not a second copy of {@code FlywayWiringTest}, which already catches a
 * mis-qualified initializer and a wrong schema by static analysis — verified, by mutation, to catch
 * them before this test exists. What it adds is the part static analysis cannot reach: that two
 * {@code Flyway} beans can be <em>instantiated together</em> and both actually migrate. Declaring a
 * second one backs off autoconfiguration for everything, so by-type injection of {@code Flyway}
 * becomes ambiguous and anything still relying on it fails only when a context with both is built.
 * Until this test, the one-bean case was checked by booting the application by hand and the
 * two-bean case not at all.
 *
 * <p>It also pins the property the whole arrangement is for: the contexts may reuse version
 * numbers, because each keeps its own history. A merged history is the failure mode that surfaces
 * as "found more than one migration with version 1", naming neither a schema nor a configuration.
 */
@JooqTest
@Import({AccountFlywayConfig.class, CustomerFlywayConfig.class})
class PerBoundedContextMigrationTest extends AbstractContainerTest {

  private static final String MIGRATION_LOCATION = "classpath*:db/migration/%s/V*.sql";

  @Autowired private DataSource dataSource;

  /**
   * Counted from the classpath rather than hardcoded, so a migration whose filename Flyway does not
   * recognise shows up as a mismatch instead of passing unnoticed.
   */
  @ParameterizedTest
  @ValueSource(strings = {"account", "customer"})
  void a_bounded_context_applies_every_migration_it_ships(String schema) {
    // Arrange
    int onDisk = migrationFileCount(schema);

    // Act
    long applied =
        scalar(
            "SELECT count(*) FROM %s.flyway_schema_history WHERE success AND version IS NOT NULL"
                .formatted(schema));

    // Assert
    assertAll(
        () -> assertThat(onDisk).as("%s ships no migrations at all", schema).isPositive(),
        () ->
            assertThat(applied)
                .as(
                    "every migration under db/migration/%s must be applied to schema %s",
                    schema, schema)
                .isEqualTo(onDisk));
  }

  /**
   * A merged history is the failure this whole design exists to prevent, and its symptom points
   * elsewhere: with both instances pointed at {@code db/migration}, Flyway scans recursively and
   * fails with "found more than one migration with version 1", naming no schema and no
   * configuration.
   */
  @Test
  void each_bounded_context_keeps_its_own_history_in_its_own_schema() {
    // Arrange
    long accountHistories = historyTablesIn("account");
    long customerHistories = historyTablesIn("customer");

    // Act
    long shared =
        scalar(
            """
            SELECT count(*) FROM (
              SELECT version FROM account.flyway_schema_history WHERE version IS NOT NULL
              INTERSECT
              SELECT version FROM customer.flyway_schema_history WHERE version IS NOT NULL
            ) AS overlapping
            """);

    // Assert
    assertAll(
        () -> assertThat(accountHistories).as("account has its own history table").isEqualTo(1),
        () -> assertThat(customerHistories).as("customer has its own history table").isEqualTo(1),
        () ->
            assertThat(shared)
                .as(
                    "the contexts share version numbers, which is the point of separate histories —"
                        + " so an overlap here proves they are separate, not merged")
                .isPositive());
  }

  /**
   * ADR-009 lists "logical replication carries {@code account.outbox} and nothing else" among its
   * constraints and invariants, and it is the one control in that ADR with nothing behind it: V18
   * asserts it at apply time, so a later migration that publishes a customer table, or anything
   * that empties the publication, is caught by nothing.
   *
   * <p>Genuinely exercised rather than vacuous: V15 creates the publication {@code FOR ALL TABLES}
   * in this container when the connector has not already done so, so the narrowing really happens
   * here.
   *
   * <p>Both directions matter, and for different reasons. Publishing more is the data-protection
   * failure — every customer row image decoded into the replication stream and shipped to the Kafka
   * Connect JVM, where only connector-side filters keep personal data off a topic. Publishing less
   * is the availability failure — the outbox pipeline dies silently, and the outbox depth gauge
   * fails into the healthy band.
   */
  @Test
  void logical_replication_carries_the_outbox_and_nothing_else() {
    // Arrange / Act
    List<String> published =
        strings(
            "SELECT format('%s.%s', schemaname, tablename) FROM pg_publication_tables"
                + " ORDER BY schemaname, tablename");

    // Assert
    assertThat(published)
        .as("no personal data may reach the replication stream, and the outbox must still reach it")
        .containsExactly("account.outbox");
  }

  /**
   * That the predicate has teeth, asserted by breaking the publication inside a rolled-back
   * transaction.
   *
   * <p>This is the half carrying the weight, and the reason is worth stating because it is not
   * obvious. Asserting the predicate returns true on a freshly migrated container is close to
   * tautological — V18 asserted the same thing moments earlier, and because the test and the
   * migration deliberately share one definition, weakening that definition weakens both in lockstep
   * and neither notices. Verified: a mutation dropping the row-filter clause from the function
   * leaves every assertion green.
   *
   * <p>What cannot be faked is whether the predicate distinguishes a working publication from an
   * inert one. So this creates the inert state and requires the answer to change. Every case below
   * still lists {@code account.outbox} and carries nothing, which is exactly what a membership-only
   * check waves through.
   *
   * <p>Rolled back, so nothing leaks — {@code ALTER PUBLICATION} is transactional.
   */
  @Test
  void the_canonical_check_rejects_a_publication_that_carries_nothing() {
    // Arrange
    String canonical = "SELECT account.dbz_publication_is_canonical()::int";

    // Act / Assert
    assertAll(
        () ->
            assertThat(
                    scalarAfter(
                        canonical,
                        "ALTER PUBLICATION dbz_publication"
                            + " SET (publish = 'update, delete, truncate')"))
                // Clears pubinsert and NOTHING else, on purpose: the first version of this case
                // disabled truncate too, so it passed on the truncate clause and left the one flag
                // the
                // outbox actually depends on untested.
                .as("inserts disabled: the outbox emits only INSERTs, so this publication is inert")
                .isZero(),
        () ->
            assertThat(
                    scalarAfter(
                        canonical,
                        "ALTER PUBLICATION dbz_publication SET (publish_via_partition_root = false)"))
                .as("attribution to partition children stops table.include.list matching")
                .isZero(),
        () ->
            assertThat(
                    scalarAfter(
                        canonical,
                        "ALTER PUBLICATION dbz_publication ADD TABLES IN SCHEMA customer"))
                .as("a schema clause is the one-statement route to the personal-data leak")
                .isZero(),
        () ->
            assertThat(
                    scalarAfter(
                        canonical, "ALTER PUBLICATION dbz_publication DROP TABLE account.outbox"))
                .as("a publication listing nothing is a dead pipeline reported as healthy")
                .isZero(),
        () ->
            assertThat(
                    scalarAfter(
                        canonical,
                        "ALTER PUBLICATION dbz_publication SET TABLE account.outbox"
                            + " WHERE (aggregate_type = 'NoSuchAggregate')"))
                .as("a row filter matching no row carries nothing while the table stays listed")
                .isZero(),
        () ->
            assertThat(
                    scalarAfter(
                        canonical,
                        "ALTER PUBLICATION dbz_publication SET TABLE account.outbox (event_id, occurred_at)"))
                .as("a column list silently drops the payload the outbox router needs")
                .isZero());
  }

  /**
   * The standing form of V18's post-condition. V18 asserts it once, when it runs; this is what
   * covers a later migration touching the publication, or an operator flipping {@code publish} on a
   * long-lived database.
   *
   * <p>It matters more than it did before this release, because two other changes removed the
   * signals that would otherwise catch an inert publication: the connector now runs with autocreate
   * disabled, so it no longer rewrites membership, and heartbeats keep the replication slot
   * confirming its LSN — so neither slot alert fires, and a pipeline producing zero records trips
   * no consumer-lag alert either. Rows would land in the outbox, partitions would age out, and
   * every gauge would stay green.
   */
  @Test
  void the_outbox_publication_is_not_merely_present_but_working() {
    // Arrange / Act
    long canonical = scalar("SELECT account.dbz_publication_is_canonical()::int");

    // Assert
    assertThat(canonical)
        .as(
            "dbz_publication must publish exactly account.outbox, via the partition root, with all"
                + " operations enabled and no row filter or column list")
        .isEqualTo(1L);
  }

  /** Both contexts are migrated, so both sets of tables must be present in one running context. */
  @Test
  void both_contexts_are_migrated_by_the_same_application_context() {
    // Arrange / Act
    long accountTables = tableCountIn("account");
    long customerTables = tableCountIn("customer");

    // Assert
    assertAll(
        () -> assertThat(accountTables).isPositive(),
        () -> assertThat(customerTables).isPositive());
  }

  private static int migrationFileCount(String schema) {
    try {
      return new PathMatchingResourcePatternResolver()
          .getResources(MIGRATION_LOCATION.formatted(schema))
          .length;
    } catch (IOException e) {
      throw new AssertionError("could not list migrations for " + schema, e);
    }
  }

  private long historyTablesIn(String schema) {
    return tableCount(schema, "AND table_name = 'flyway_schema_history'");
  }

  private long tableCountIn(String schema) {
    return tableCount(schema, "AND table_name <> 'flyway_schema_history'");
  }

  private long tableCount(String schema, String extraPredicate) {
    return scalar(
        "SELECT count(*) FROM information_schema.tables WHERE table_schema = '%s' %s"
            .formatted(schema, extraPredicate));
  }

  /** Evaluates a scalar with statements applied first, then rolls the whole thing back. */
  private long scalarAfter(String scalarSql, String... statements) {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        for (String sql : statements) {
          statement.execute(sql);
        }
        try (ResultSet rows = statement.executeQuery(scalarSql)) {
          assertThat(rows.next()).as("query returned no row: %s", scalarSql).isTrue();
          return rows.getLong(1);
        }
      } finally {
        connection.rollback();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("probe failed: " + scalarSql, e);
    }
  }

  private List<String> strings(String sql) {
    List<String> values = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      while (rows.next()) {
        values.add(rows.getString(1));
      }
      return values;
    } catch (SQLException e) {
      throw new IllegalStateException("query failed: " + sql, e);
    }
  }

  private long scalar(String sql) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      assertThat(rows.next()).as("query returned no row: %s", sql).isTrue();
      return rows.getLong(1);
    } catch (SQLException e) {
      throw new IllegalStateException("query failed: " + sql, e);
    }
  }
}
