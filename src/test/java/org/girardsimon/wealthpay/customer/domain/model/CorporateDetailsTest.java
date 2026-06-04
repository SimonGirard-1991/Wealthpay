package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.girardsimon.wealthpay.customer.domain.exception.InvalidCorporateDetailsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CorporateDetailsTest {

  private static final CountryCode INCORPORATION = CountryCode.of("FR");

  @Test
  void creates_corporate_details_from_valid_fields() {
    // Act
    CorporateDetails details = new CorporateDetails("Acme S.A.", "RCS123456", INCORPORATION);

    // Assert
    assertThat(details.registeredName()).isEqualTo("Acme S.A.");
    assertThat(details.registrationNumber()).isEqualTo("RCS123456");
    assertThat(details.countryOfIncorporation()).isEqualTo(INCORPORATION);
  }

  @Test
  void strips_surrounding_whitespace() {
    // Act
    CorporateDetails details = new CorporateDetails("  Acme S.A. ", " RCS123456 ", INCORPORATION);

    // Assert
    assertThat(details.registeredName()).isEqualTo("Acme S.A.");
    assertThat(details.registrationNumber()).isEqualTo("RCS123456");
  }

  @ParameterizedTest
  @MethodSource("oneRequiredFieldNull")
  void rejects_when_a_required_field_is_null(
      String registeredName, String registrationNumber, CountryCode country) {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new CorporateDetails(registeredName, registrationNumber, country));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void rejects_a_blank_registered_name(String blank) {
    // Act ... Assert
    assertThatExceptionOfType(InvalidCorporateDetailsException.class)
        .isThrownBy(() -> new CorporateDetails(blank, "RCS123456", INCORPORATION));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void rejects_a_blank_registration_number(String blank) {
    // Act ... Assert
    assertThatExceptionOfType(InvalidCorporateDetailsException.class)
        .isThrownBy(() -> new CorporateDetails("Acme S.A.", blank, INCORPORATION));
  }

  private static Stream<Arguments> oneRequiredFieldNull() {
    return Stream.of(
        arguments(null, "RCS123456", INCORPORATION),
        arguments("Acme S.A.", null, INCORPORATION),
        arguments("Acme S.A.", "RCS123456", null));
  }
}
