package org.girardsimon.wealthpay.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Supplies a PostgreSQL container and nothing else. It knows about no bounded context: each BC
 * extends it with a subclass that imports its own Flyway configuration, so a BC's tests migrate
 * only its own schema and a broken migration fails only its own suite.
 *
 * <p>Migrations are wired through {@code @Import} rather than {@code spring.flyway.*} properties,
 * which are inert once a BC declares its own {@code Flyway} bean. That matters because the
 * container tests are {@code @JooqTest} slices: they do not component-scan, but Flyway
 * auto-configuration is still reachable in the slice (via {@code @AutoConfigureJooq
 * → @AutoConfigureDataSourceInitialization}). An un-imported configuration would therefore leave
 * auto-configuration quietly in charge of the test path while production used the beans.
 *
 * <p><strong>Started once for the JVM, deliberately not through {@code @Testcontainers} and
 * {@code @Container}.</strong> That extension treats a static container as per-class state and
 * stops it in {@code afterAll}, so the next test class starts a fresh container on a fresh random
 * port — while Spring, whose context cache is keyed on configuration, hands that class the context
 * built for the previous port. Two test classes that share a slice configuration therefore fail
 * with "connection refused" against a port nothing is listening on, and the failure names the pool
 * rather than the cause.
 *
 * <p>Nothing masked this until two classes first shared a configuration: every earlier container
 * test happened to declare a distinct {@code @Import} set, so each got its own context and
 * re-resolved the port. Starting the container in a static initialiser and never stopping it
 * removes the coupling between container lifetime and class lifetime entirely; Ryuk removes it when
 * the JVM exits.
 */
public abstract class AbstractContainerTest {

  /**
   * The connection ceiling is raised because one container now serves the whole run. Spring caches
   * a context per distinct slice configuration and each cached context keeps a Hikari pool open, so
   * the run holds roughly (number of distinct container-test configurations) × pool size at once,
   * not the handful any single test uses. Left at PostgreSQL's default 100 that fails as {@code
   * FATAL: sorry, too many clients already}, reported against whichever context happened to be
   * built last.
   *
   * <p>{@code fsync=off} is restored, not added: {@code PostgreSQLContainer}'s constructor sets the
   * command to {@code postgres -c fsync=off}, and {@code withCommand} replaces that array wholesale
   * rather than appending to it — so tuning anything here silently re-enables real fsyncs for the
   * whole suite.
   */
  protected static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18")
          .withDatabaseName("wealthpay")
          .withUsername("wealthpay")
          .withPassword("wealthpay")
          .withCommand("postgres", "-c", "fsync=off", "-c", "max_connections=300");

  static {
    try {
      POSTGRES.start();
    } catch (RuntimeException e) {
      // Otherwise this surfaces as ExceptionInInitializerError on the first container test and
      // NoClassDefFoundError on every one after it, neither of which names Docker.
      throw new IllegalStateException(
          "could not start the PostgreSQL test container — container tests require a running Docker"
              + " daemon",
          e);
    }
  }

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // Raising max_connections alone only moves the cliff: the context cache holds up to 32
    // contexts,
    // so ~30 slice configurations would reach it again. Letting a finished context's pool drain
    // removes the coupling between "how many configurations exist" and "how many connections are
    // held". maximum-pool-size stays at its default 10, because several repository tests contend
    // with ten threads on purpose and a smaller ceiling would turn a race under test into a
    // pool-timeout deadlock.
    registry.add("spring.datasource.hikari.minimum-idle", () -> 0);
    registry.add("spring.datasource.hikari.idle-timeout", () -> 10_000);
  }
}
