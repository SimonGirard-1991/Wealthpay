package org.girardsimon.wealthpay.customer.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Runs statements straight through JDBC, because the subject under test is the schema rather than
 * any mapping over it. Going through jOOQ or a repository here would put a translation layer
 * between the assertion and the constraint it is about.
 *
 * <p>Two execution modes, and the distinction is not incidental:
 *
 * <ul>
 *   <li><b>Autocommit</b>, for anything a single statement can decide. PostgreSQL aborts an entire
 *       transaction on a constraint violation, so a shared transaction would leave every assertion
 *       after the first failing with "current transaction is aborted" instead of the violation it
 *       was checking. A rejected statement writes nothing, so there is nothing to clean up either —
 *       which matters because the audit tables cannot be cleaned, being append-only.
 *   <li><b>One transaction</b>, for the deferred constraint triggers, whose exception arrives at
 *       {@code COMMIT}. An aggregate's parts — a customer and its nationalities, a decision and its
 *       matches — are only consistent once all of them are written, so a fixture that writes them
 *       one autocommitted statement at a time is genuinely invalid rather than merely awkward.
 * </ul>
 */
final class SchemaProbe {

  private static final String RAISED_EXCEPTION = "P0001";
  private static final String NOT_NULL_VIOLATION = "23502";
  private static final int TIMEOUT_SECONDS = 10;
  private static final long BLOCK_PROBE_MILLIS = 1_500;

  private final DataSource dataSource;

  SchemaProbe(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  void execute(String sql) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException("statement was expected to succeed: " + sql, e);
    }
  }

  /**
   * Commits several statements together. Fails the test if any of them, or the commit, is rejected.
   */
  void executeAll(String... statements) {
    SQLException rejection = runInTransaction(statements);
    if (rejection != null) {
      throw new IllegalStateException(
          "transaction was expected to commit: " + join(statements), rejection);
    }
  }

  long count(String sql) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      assertThat(rows.next()).as("query returned no row: %s", sql).isTrue();
      return rows.getLong(1);
    } catch (SQLException e) {
      throw new IllegalStateException("query was expected to succeed: " + sql, e);
    }
  }

  /**
   * Asserts on the constraint name reported by the server rather than on the message text, so the
   * assertion names the guard it is about. A statement rejected by a <em>different</em> constraint
   * than intended would otherwise pass and leave the intended one untested.
   */
  void expectViolation(String sql, String constraintName) {
    assertThat(rejectionOf(sql).getConstraint())
        .as("expected %s to reject this statement: %s", constraintName, sql)
        .isEqualTo(constraintName);
  }

  /** Same, for a rejection that only a whole transaction can produce. */
  void expectViolationInTransaction(String constraintName, String... statements) {
    assertThat(rejectionOfTransaction(statements).getConstraint())
        .as("expected %s to reject: %s", constraintName, join(statements))
        .isEqualTo(constraintName);
  }

  /** A {@code NOT NULL} violation names the column rather than a constraint. */
  void expectNotNullViolation(String sql, String columnName) {
    ServerErrorMessage error = rejectionOf(sql);
    assertThat(error.getSQLState())
        .as("expected a not-null violation: %s", sql)
        .isEqualTo(NOT_NULL_VIOLATION);
    assertThat(error.getColumn()).isEqualTo(columnName);
  }

  /**
   * Triggers {@code RAISE EXCEPTION}, which arrives as {@code P0001} and carries only a message.
   */
  void expectRaisedRejection(String sql, String messageFragment) {
    assertRaised(rejectionOf(sql), messageFragment, sql);
  }

  /** Same, for the deferred constraint triggers, which raise at {@code COMMIT}. */
  void expectRaisedRejectionInTransaction(String messageFragment, String... statements) {
    assertRaised(rejectionOfTransaction(statements), messageFragment, join(statements));
  }

  /**
   * The same assertion with {@code session_replication_role = replica} in force — the one statement
   * that defeats ordinary triggers and referential actions together, and the whole reason the
   * guards are declared {@code ENABLE ALWAYS}.
   *
   * <p>{@code SET LOCAL} rather than {@code SET}, so the setting is scoped to the transaction and
   * cannot ride a pooled connection into another test.
   */
  void expectRaisedRejectionUnderReplicaRole(String sql, String messageFragment) {
    assertRaised(
        rejectionOfTransaction("SET LOCAL session_replication_role = replica", sql),
        messageFragment,
        sql);
  }

  /**
   * Commits a statement with one guard suspended, through the same {@code ALTER TABLE ... DISABLE
   * TRIGGER} idiom the future retention purge is required to use. Needed wherever a test asserts
   * what happens <em>after</em> a privileged deletion, which the guards otherwise make unreachable.
   *
   * <p>Re-enabling is part of the idiom, not tidiness. {@code DISABLE TRIGGER} is DDL and DDL is
   * transactional, so committing the transaction commits the disabled state with it. Omitting this
   * left the guard off for the rest of the JVM and turned a later test's assertion from "the
   * trigger refused this" into "a foreign key refused this" — weakening every subsequent check
   * instead of failing. Whatever implements the retention purge inherits the same obligation.
   */
  void executeWithGuardSuspended(String table, String triggerName, String sql) {
    executeAll(
        disableTrigger(table, triggerName),
        sql,
        "ALTER TABLE customer.%s ENABLE ALWAYS TRIGGER %s".formatted(table, triggerName));
  }

  /**
   * Runs the maintenance path and rolls back, reporting how many rows the delete would have
   * removed. The retention purge has no other way in, so this needs to keep working; it is also
   * what makes {@code ENABLE ALWAYS} affordable, since the alternative idiom is the bypass it
   * exists to break.
   */
  long deletableWithGuardSuspended(String table, String triggerName) {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(disableTrigger(table, triggerName));
        return statement.executeUpdate("DELETE FROM customer.%s".formatted(table));
      } finally {
        connection.rollback();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("the sanctioned maintenance path failed on " + table, e);
    }
  }

  /**
   * Asserts that {@code sql} cannot commit while another transaction holds the row lock on customer
   * {@code customerId}, and that it does commit once that lock is released.
   *
   * <p>This is how the cardinality trigger's {@code SELECT ... FOR UPDATE} on the parent is pinned,
   * and the obvious alternative is worth recording because it was written first and proved
   * <em>vacuous</em>: two concurrent deletes expecting one refusal passes with or without the lock,
   * because at READ COMMITTED the second transaction's count takes a fresh snapshot and sees the
   * first one's committed delete anyway. The window the lock actually closes is the narrow one
   * between one transaction's count and its commit, and no client-side barrier can be placed inside
   * a trigger body to hit it. So the lock is asserted by its observable effect instead: the trigger
   * contends for the parent row.
   *
   * <p>Not timing-sensitive in the direction that matters. Completing while the lock is held proves
   * the trigger is not taking it; the wait exists only to give it the opportunity.
   */
  void expectBlockedWhileCustomerLocked(String customerId, String sql) {
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      try (Statement lock = holder.createStatement();
          ExecutorService executor = Executors.newSingleThreadExecutor()) {
        lock.execute(
            "SELECT 1 FROM customer.customer WHERE id = '%s' FOR UPDATE".formatted(customerId));

        Future<SQLException> contender = executor.submit(() -> runInTransaction(sql));
        assertThat(completedWithin(contender, BLOCK_PROBE_MILLIS))
            .as(
                "expected the deferred trigger to contend for the parent row lock, so this could not"
                    + " commit while it was held: %s",
                sql)
            .isFalse();

        holder.rollback();
        assertThat(completedWithin(contender, TIMEOUT_SECONDS * 1000L))
            .as("expected this to proceed once the lock was released: %s", sql)
            .isTrue();
      } finally {
        holder.rollback();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("could not probe under a held lock: " + sql, e);
    }
  }

  private static boolean completedWithin(Future<SQLException> attempt, long millis) {
    try {
      attempt.get(millis, TimeUnit.MILLISECONDS);
      return true;
    } catch (TimeoutException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while awaiting the contending transaction", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("the contending transaction failed unexpectedly", e);
    }
  }

  static String disableTrigger(String table, String triggerName) {
    return "ALTER TABLE customer.%s DISABLE TRIGGER %s".formatted(table, triggerName);
  }

  private static void assertRaised(ServerErrorMessage error, String messageFragment, String what) {
    assertThat(error.getSQLState())
        .as("expected a trigger to raise for: %s", what)
        .isEqualTo(RAISED_EXCEPTION);
    assertThat(error.getMessage()).contains(messageFragment);
  }

  private ServerErrorMessage rejectionOf(String sql) {
    return serverErrorFrom(rejectionOfStatement(sql), sql);
  }

  private ServerErrorMessage rejectionOfTransaction(String... statements) {
    return serverErrorFrom(runInTransaction(statements), join(statements));
  }

  private static ServerErrorMessage serverErrorFrom(SQLException rejection, String what) {
    if (rejection == null) {
      throw new AssertionError("the database accepted what it should have rejected: " + what);
    }
    if (!(rejection instanceof PSQLException postgres)) {
      throw new IllegalStateException(
          "rejected by the driver rather than the server: " + what, rejection);
    }
    ServerErrorMessage error = postgres.getServerErrorMessage();
    assertThat(error).as("rejection carried no server error message: %s", what).isNotNull();
    return error;
  }

  private SQLException rejectionOfStatement(String sql) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
      return null;
    } catch (SQLException e) {
      return e;
    }
  }

  /** Returns the rejection, or {@code null} if the whole transaction committed. */
  private SQLException runInTransaction(String... statements) {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        for (String sql : statements) {
          statement.execute(sql);
        }
        connection.commit();
        return null;
      } catch (SQLException e) {
        connection.rollback();
        return e;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("could not probe: " + join(statements), e);
    }
  }

  private static String join(String... statements) {
    return String.join("; ", statements);
  }
}
