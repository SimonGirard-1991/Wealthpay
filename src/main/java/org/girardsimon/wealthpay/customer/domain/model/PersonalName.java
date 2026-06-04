package org.girardsimon.wealthpay.customer.domain.model;

import org.girardsimon.wealthpay.customer.domain.exception.InvalidPersonalNameException;

/** A person's name. {@code middleName} is optional; a {@code null} value means none. */
public record PersonalName(String givenName, String middleName, String familyName) {

  public PersonalName {
    if (givenName == null || familyName == null) {
      throw new IllegalArgumentException("Given name and family name must not be null");
    }
    givenName = givenName.strip();
    familyName = familyName.strip();
    if (givenName.isEmpty() || familyName.isEmpty()) {
      throw new InvalidPersonalNameException("Given name and family name must not be blank");
    }
    middleName = normalizeMiddleName(middleName);
  }

  private static String normalizeMiddleName(String middleName) {
    if (middleName == null) {
      return null;
    }
    String stripped = middleName.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  public static PersonalName of(String givenName, String familyName) {
    return new PersonalName(givenName, null, familyName);
  }

  public static PersonalName of(String givenName, String middleName, String familyName) {
    return new PersonalName(givenName, middleName, familyName);
  }
}
