package org.girardsimon.wealthpay.customer.domain.model;

import java.util.Locale;
import java.util.Set;
import org.girardsimon.wealthpay.customer.domain.exception.InvalidCountryCodeException;

public record CountryCode(String value) {
  // Validated against the JDK's ISO 3166-1 alpha-2 set, resolved once at class load. This set is
  // JDK-version-dependent (e.g. XK/Kosovo is absent) - swap for an explicit policy/sanctions
  // allow-list when onboarding rules land.
  private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

  public CountryCode {
    if (value == null) {
      throw new IllegalArgumentException("Country code must not be null");
    }
    String canonical = value.toUpperCase(Locale.ROOT);
    if (!ISO_COUNTRIES.contains(canonical)) {
      throw new InvalidCountryCodeException("Country code is not a valid ISO 3166-1 alpha-2 code");
    }
    value = canonical;
  }

  public static CountryCode of(String value) {
    return new CountryCode(value);
  }
}
