package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.LocalDate;
import java.time.Month;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IndividualDetailsTest {

  private static final PersonalName NAME = PersonalName.of("John", "Doe");
  private static final LocalDate DOB = LocalDate.of(1990, Month.JANUARY, 1);
  private static final Nationalities NATIONALITIES = Nationalities.of(CountryCode.of("FR"));
  private static final CountryCode RESIDENCE = CountryCode.of("BE");

  @Test
  void creates_individual_details_from_valid_fields() {
    // Act
    IndividualDetails details =
        new IndividualDetails(NAME, DOB, Gender.MALE, NATIONALITIES, RESIDENCE);

    // Assert
    assertThat(details.name()).isEqualTo(NAME);
    assertThat(details.dateOfBirth()).isEqualTo(DOB);
    assertThat(details.gender()).isEqualTo(Gender.MALE);
    assertThat(details.nationalities()).isEqualTo(NATIONALITIES);
    assertThat(details.countryOfResidence()).isEqualTo(RESIDENCE);
  }

  @ParameterizedTest
  @MethodSource("oneRequiredFieldNull")
  void rejects_when_a_required_field_is_null(
      PersonalName name,
      LocalDate dateOfBirth,
      Gender gender,
      Nationalities nationalities,
      CountryCode countryOfResidence) {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                new IndividualDetails(
                    name, dateOfBirth, gender, nationalities, countryOfResidence));
  }

  private static Stream<Arguments> oneRequiredFieldNull() {
    return Stream.of(
        arguments(null, DOB, Gender.MALE, NATIONALITIES, RESIDENCE),
        arguments(NAME, null, Gender.MALE, NATIONALITIES, RESIDENCE),
        arguments(NAME, DOB, null, NATIONALITIES, RESIDENCE),
        arguments(NAME, DOB, Gender.MALE, null, RESIDENCE),
        arguments(NAME, DOB, Gender.MALE, NATIONALITIES, null));
  }
}
