package org.girardsimon.wealthpay.customer.infrastructure.db;

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
 * Migrations for the {@code customer} schema, declared alongside its repositories rather than in a
 * central registry, so adding a bounded context never means editing a shared file.
 *
 * <p>This instance is unordered relative to the {@code account} one, which matters here because the
 * customer schema holds personal data and must not exist while a logical replication publication
 * carries more than {@code account.outbox}. The ordering is not enforced by Flyway and does not
 * need to be: both instances finish during context refresh, before the web server accepts a
 * request, so on the ordering where the customer migrates first, its tables are empty for the whole
 * window and produce no WAL to decode. The invariant is a release-level one — this schema ships in
 * the same release as the migration that narrows the publication.
 */
@Configuration
@ConditionalOnBooleanProperty(name = "spring.flyway.enabled", matchIfMissing = true)
public class CustomerFlywayConfig {

  private static final String SCHEMA = "customer";

  @Bean
  public Flyway customerFlyway(DataSource dataSource, ResourceLoader resourceLoader) {
    return ModuleFlyway.forSchema(dataSource, resourceLoader.getClassLoader(), SCHEMA);
  }

  /**
   * Required, not merely explicit: declaring any {@code Flyway} bean backs off the
   * autoconfiguration that was the only thing calling {@code migrate()}. The qualifier is what
   * keeps this initializer pointing at this bounded context's instance — by-type injection is now
   * ambiguous, and an initializer bound to the wrong bean migrates that schema twice, idempotently
   * and silently, while never creating this one.
   */
  @Bean
  public FlywayMigrationInitializer customerFlywayInitializer(
      @Qualifier("customerFlyway") Flyway customerFlyway) {
    return new FlywayMigrationInitializer(customerFlyway);
  }
}
