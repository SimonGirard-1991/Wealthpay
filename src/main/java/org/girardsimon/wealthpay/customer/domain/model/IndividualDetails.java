package org.girardsimon.wealthpay.customer.domain.model;

import java.time.LocalDate;

public record IndividualDetails(
    PersonalName name, LocalDate dateOfBirth, Gender gender, CountryCode nationality)
    implements CustomerDetails {

  public IndividualDetails {
    if (name == null || dateOfBirth == null || gender == null || nationality == null) {
      throw new IllegalArgumentException(
          "Individual details require name, date of birth, gender and nationality");
    }
  }
}
