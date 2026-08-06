package org.girardsimon.wealthpay.customer.domain.model;

import java.util.Map;
import java.util.Set;

/** A legal entity has neither a nationality nor a residence, only a country of incorporation. */
public record CorporateSubject(CountryCode countryOfIncorporation) implements AdmissionSubject {

  public CorporateSubject {
    requireComplete(countryOfIncorporation);
  }

  private static void requireComplete(CountryCode countryOfIncorporation) {
    if (countryOfIncorporation == null) {
      throw new IllegalArgumentException("A corporate subject requires a country of incorporation");
    }
  }

  @Override
  public Map<ConnectingFactor, Set<CountryCode>> connections() {
    return Map.of(ConnectingFactor.INCORPORATION, Set.of(countryOfIncorporation));
  }

  @Override
  public ConnectingFactor establishmentFactor() {
    return ConnectingFactor.INCORPORATION;
  }
}
