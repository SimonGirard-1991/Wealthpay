package org.girardsimon.wealthpay.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModuleFlywayTest {

  @Mock private DataSource dataSource;

  @Test
  void forSchema_derives_both_the_schema_and_the_migration_location_from_the_module_name() {
    // Arrange
    String schema = "customer";

    // Act
    Configuration configuration = configurationFor(schema);

    // Assert
    assertThat(configuration.getSchemas()).containsExactly("customer");
    assertThat(configuration.getDefaultSchema()).isEqualTo("customer");
    assertThat(configuration.getLocations())
        .extracting(Location::getDescriptor)
        .containsExactly("classpath:db/migration/customer");
  }

  /**
   * These are pinned in code precisely so no {@code spring.flyway.*} key can reach them — those
   * keys are inert once a BC declares its own Flyway bean. Asserting them here makes the guarantee
   * a test failure rather than a code review catch.
   */
  @Test
  void forSchema_pins_the_settings_that_no_configuration_key_can_override() {
    // Arrange / Act
    Configuration configuration = configurationFor("account");

    // Assert
    assertThat(configuration.isCleanDisabled()).isTrue();
    assertThat(configuration.isFailOnMissingLocations()).isTrue();
    assertThat(configuration.isCreateSchemas()).isTrue();
  }

  private Configuration configurationFor(String schema) {
    return ModuleFlyway.forSchema(dataSource, getClass().getClassLoader(), schema)
        .getConfiguration();
  }
}
