package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.girardsimon.wealthpay.customer.domain.exception.InvalidCountryCodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CountryCodeTest {

  @Test
  void of_accepts_a_valid_iso_alpha2_code() {
    // Arrange
    String value = "FR";

    // Act
    CountryCode country = CountryCode.of(value);

    // Assert
    assertThat(country.value()).isEqualTo("FR");
  }

  @Test
  void normalizes_to_uppercase_canonical_form() {
    // Arrange
    String lower = "fr";

    // Act
    CountryCode country = CountryCode.of(lower);

    // Assert
    assertThat(country.value()).isEqualTo("FR");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ZZ", // not an assigned ISO country
        "F", // too short
        "FRA", // alpha-3, not alpha-2
        "12", // digits
        "" // empty
      })
  void rejects_a_value_that_is_not_an_iso_alpha2_code(String invalid) {
    // Act ... Assert
    assertThatExceptionOfType(InvalidCountryCodeException.class)
        .isThrownBy(() -> CountryCode.of(invalid));
  }

  @Test
  void rejects_null() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> CountryCode.of(null));
  }
}
