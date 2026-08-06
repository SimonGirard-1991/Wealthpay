package org.girardsimon.wealthpay.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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
 */
@Testcontainers
public abstract class AbstractContainerTest {

  @Container
  protected static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18")
          .withDatabaseName("wealthpay")
          .withUsername("wealthpay")
          .withPassword("wealthpay");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }
}
