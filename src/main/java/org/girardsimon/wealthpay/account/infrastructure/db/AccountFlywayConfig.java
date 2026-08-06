package org.girardsimon.wealthpay.account.infrastructure.db;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.girardsimon.wealthpay.shared.config.ModuleFlyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/**
 * Migrations for the {@code account} schema. Each bounded context declares its own pair of beans;
 * adding a BC therefore means adding a configuration class next to its repositories, never editing
 * a central registry.
 *
 * <p>The condition keeps {@code spring.flyway.enabled} honest. Without it the key would gate only
 * the autoconfiguration — leaving these beans migrating regardless, while silently dropping the
 * {@code DatabaseInitializationDependencyConfigurer} import that orders DataSource consumers after
 * migration. A kill switch that does not kill and quietly removes an ordering guarantee, is worse
 * than none.
 */
@Configuration
@ConditionalOnBooleanProperty(name = "spring.flyway.enabled", matchIfMissing = true)
public class AccountFlywayConfig {

  private static final String SCHEMA = "account";

  @Bean
  public Flyway accountFlyway(DataSource dataSource, ResourceLoader resourceLoader) {
    return ModuleFlyway.forSchema(dataSource, resourceLoader.getClassLoader(), SCHEMA);
  }

  /**
   * Required, not merely explicit: the backed-off autoconfiguration was the only thing calling
   * {@code migrate()}. The qualifier is deliberate — once a second BC declares a {@code Flyway}
   * bean, by-type injection becomes ambiguous and by-parameter-name resolution would depend on
   * {@code -parameters} surviving in the build.
   */
  @Bean
  public FlywayMigrationInitializer accountFlywayInitializer(
      @Qualifier("accountFlyway") Flyway accountFlyway) {
    return new FlywayMigrationInitializer(accountFlyway);
  }
}
