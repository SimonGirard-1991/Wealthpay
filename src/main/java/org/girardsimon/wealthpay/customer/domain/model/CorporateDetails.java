package org.girardsimon.wealthpay.customer.domain.model;

import org.girardsimon.wealthpay.customer.domain.exception.InvalidCorporateDetailsException;

public record CorporateDetails(
    String registeredName, String registrationNumber, CountryCode countryOfIncorporation)
    implements CustomerDetails {

  public CorporateDetails {
    if (registeredName == null || registrationNumber == null || countryOfIncorporation == null) {
      throw new IllegalArgumentException(
          "Corporate details require registered name, registration number and country");
    }
    registeredName = registeredName.strip();
    registrationNumber = registrationNumber.strip();
    if (registeredName.isEmpty() || registrationNumber.isEmpty()) {
      throw new InvalidCorporateDetailsException(
          "Registered name and registration number must not be blank");
    }
  }
}
