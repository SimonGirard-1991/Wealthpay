package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IndividualDetailsTest {

  private static final PersonalName NAME = PersonalName.of("John", "Doe");
  private static final LocalDate DOB = LocalDate.of(1990, 1, 1);
  private static final CountryCode NATIONALITY = CountryCode.of("FR");

  @Test
  void creates_individual_details_from_valid_fields() {
    // Act
    IndividualDetails details = new IndividualDetails(NAME, DOB, Gender.MALE, NATIONALITY);

    // Assert
    assertThat(details.name()).isEqualTo(NAME);
    assertThat(details.dateOfBirth()).isEqualTo(DOB);
    assertThat(details.gender()).isEqualTo(Gender.MALE);
    assertThat(details.nationality()).isEqualTo(NATIONALITY);
  }

  @ParameterizedTest
  @MethodSource("oneRequiredFieldNull")
  void rejects_when_a_required_field_is_null(
      PersonalName name, LocalDate dateOfBirth, Gender gender, CountryCode nationality) {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new IndividualDetails(name, dateOfBirth, gender, nationality));
  }

  private static Stream<Arguments> oneRequiredFieldNull() {
    return Stream.of(
        arguments(null, DOB, Gender.MALE, NATIONALITY),
        arguments(NAME, null, Gender.MALE, NATIONALITY),
        arguments(NAME, DOB, null, NATIONALITY),
        arguments(NAME, DOB, Gender.MALE, null));
  }
}
