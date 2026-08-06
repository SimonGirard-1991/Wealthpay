package org.girardsimon.wealthpay.account.infrastructure.db;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@code spring.flyway.enabled} is the reason this configuration carries a condition at all: Spring
 * Boot's own copy of the key gates only the autoconfiguration, so without the condition these beans
 * would migrate regardless and the switch would be a lie. Nothing else in the suite exercises that,
 * so removing the annotation would otherwise be a green build.
 */
@ExtendWith(MockitoExtension.class)
class AccountFlywayConfigTest {

  @Mock private DataSource dataSource;

  /**
   * Lazy initialization keeps the assertions at the bean-definition level — instantiating {@code
   * FlywayMigrationInitializer} would run {@code migrate()} against the mock DataSource.
   */
  private ApplicationContextRunner runner() {
    return new ApplicationContextRunner()
        .withInitializer(
            context ->
                context.addBeanFactoryPostProcessor(
                    new LazyInitializationBeanFactoryPostProcessor()))
        .withBean(DataSource.class, () -> dataSource)
        .withUserConfiguration(AccountFlywayConfig.class);
  }

  @Test
  void migrations_are_wired_when_the_switch_is_absent() {
    // Arrange
    ApplicationContextRunner runner = runner();

    // Act / Assert
    runner.run(
        context ->
            assertThat(context)
                .hasSingleBean(Flyway.class)
                .hasSingleBean(FlywayMigrationInitializer.class));
  }

  @Test
  void the_switch_actually_disables_migrations_rather_than_only_the_auto_configuration() {
    // Arrange
    ApplicationContextRunner runner = runner().withPropertyValues("spring.flyway.enabled=false");

    // Act / Assert
    runner.run(
        context ->
            assertThat(context)
                .doesNotHaveBean(Flyway.class)
                .doesNotHaveBean(FlywayMigrationInitializer.class));
  }
}
