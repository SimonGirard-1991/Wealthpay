package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.girardsimon.wealthpay.customer.domain.exception.InvalidPersonalNameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PersonalNameTest {

  @Test
  void of_given_and_family_creates_a_name_without_a_middle_name() {
    // Act
    PersonalName name = PersonalName.of("John", "Doe");

    // Assert
    assertThat(name.givenName()).isEqualTo("John");
    assertThat(name.familyName()).isEqualTo("Doe");
    assertThat(name.middleName()).isNull();
  }

  @Test
  void of_keeps_a_provided_middle_name() {
    // Act
    PersonalName name = PersonalName.of("John", "Quincy", "Adams");

    // Assert
    assertThat(name.middleName()).isEqualTo("Quincy");
  }

  @Test
  void strips_surrounding_whitespace() {
    // Act
    PersonalName name = PersonalName.of("  John ", " Quincy ", " Doe ");

    // Assert
    assertThat(name.givenName()).isEqualTo("John");
    assertThat(name.middleName()).isEqualTo("Quincy");
    assertThat(name.familyName()).isEqualTo("Doe");
  }

  @Test
  void treats_a_blank_middle_name_as_absent() {
    // Act
    PersonalName name = PersonalName.of("John", "   ", "Doe");

    // Assert
    assertThat(name.middleName()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void rejects_a_blank_given_name(String blank) {
    // Act ... Assert
    assertThatExceptionOfType(InvalidPersonalNameException.class)
        .isThrownBy(() -> PersonalName.of(blank, "Doe"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void rejects_a_blank_family_name(String blank) {
    // Act ... Assert
    assertThatExceptionOfType(InvalidPersonalNameException.class)
        .isThrownBy(() -> PersonalName.of("John", blank));
  }

  @Test
  void rejects_a_null_given_name() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> PersonalName.of(null, "Doe"));
  }

  @Test
  void rejects_a_null_family_name() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> PersonalName.of("John", null));
  }
}
