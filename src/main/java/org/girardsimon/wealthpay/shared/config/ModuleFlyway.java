package org.girardsimon.wealthpay.shared.config;

import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * Builds the {@link Flyway} instance owned by one bounded context. Each BC migrates its own schema
 * from its own location and keeps its own {@code flyway_schema_history}, so version numbers never
 * collide across BCs.
 *
 * <p>The accepted cost is that the instances are unordered relative to each other. That is safe
 * only while every migration confines itself to its own schema — an invariant nothing currently
 * enforces, and one Spring Modulith does not cover, since it constrains Java imports rather than
 * SQL.
 *
 * <p>Declaring any {@code Flyway} bean backs off {@code
 * FlywayAutoConfiguration.FlywayConfiguration} wholesale: it carries
 * {@code @ConditionalOnMissingBean(Flyway.class)} <em>and</em>
 * {@code @EnableConfigurationProperties} at class level. Everything it contributed is silently
 * gone, which is why each BC configuration is verbose on purpose:
 *
 * <ul>
 *   <li>every {@code spring.flyway.*} key except {@code enabled} is inert, so schema and location
 *       must live in code;
 *   <li>nothing calls {@code migrate()} any more, so each instance needs its own {@code
 *       FlywayMigrationInitializer};
 *   <li>{@code Callback}, {@code JavaMigration}, {@code FlywayConfigurationCustomizer} and {@code
 *       FlywayMigrationStrategy} beans are no longer consulted — a migration-audit callback, or a
 *       {@code repair()}-then-{@code migrate()} strategy, would be ignored without a warning;
 *   <li>{@code FlywayConnectionDetails} and {@code @FlywayDataSource} no longer apply, so a
 *       Flyway-specific connection — a dedicated DDL role, say — has to be supplied as the {@code
 *       DataSource} argument here rather than through configuration. ({@code @ServiceConnection} is
 *       unaffected: it configures the application {@code DataSource}, which is what these instances
 *       migrate through.)
 * </ul>
 */
public final class ModuleFlyway {

  private static final String LOCATION_PREFIX = "classpath:db/migration/";

  private ModuleFlyway() {}

  /**
   * The migration location is derived from the schema rather than passed in: it makes {@link
   * #locationOf} a convention the caller cannot deviate from, and removes a pair of adjacent
   * same-typed parameters that could be swapped.
   */
  public static Flyway forSchema(DataSource dataSource, ClassLoader classLoader, String schema) {
    Objects.requireNonNull(
        classLoader, "classLoader is required to resolve migrations from the classpath");
    return Flyway.configure(classLoader)
        .dataSource(dataSource)
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(true)
        .locations(locationOf(schema))
        // A mistyped schema would otherwise resolve to an empty location and migrate nothing.
        .failOnMissingLocations(true)
        // Pinned in code so no configuration key can re-enable clean against a production schema.
        .cleanDisabled(true)
        .load();
  }

  public static String locationOf(String schema) {
    return LOCATION_PREFIX + schema;
  }
}
