package org.girardsimon.wealthpay.customer.domain.model;

import java.time.LocalDate;

/**
 * Details of a natural person.
 *
 * <p>{@code nationalities} and {@code countryOfResidence} are separate axes and must not be
 * conflated: sanctions screen against both, licensing keys on residence for most jurisdictions, and
 * a person can hold a nationality of a market we merely lack a license in while living somewhere we
 * are authorized.
 *
 * <p>No temporal validation of {@code dateOfBirth} here (not-future, minimum age): a value object
 * has no clock. Both checks belong in the use case, which does - and a minimum age additionally
 * must not live here, being time-varying, since it would make a persisted row un-rehydratable the
 * day the threshold changes.
 */
public record IndividualDetails(
    PersonalName name,
    LocalDate dateOfBirth,
    Gender gender,
    Nationalities nationalities,
    CountryCode countryOfResidence)
    implements CustomerDetails {

  public IndividualDetails {
    requireComplete(name, dateOfBirth, gender, nationalities, countryOfResidence);
  }

  private static void requireComplete(
      PersonalName name,
      LocalDate dateOfBirth,
      Gender gender,
      Nationalities nationalities,
      CountryCode countryOfResidence) {
    if (name == null
        || dateOfBirth == null
        || gender == null
        || nationalities == null
        || countryOfResidence == null) {
      throw new IllegalArgumentException(
          "Individual details require name, date of birth, gender, nationalities and country of"
              + " residence");
    }
  }
}
