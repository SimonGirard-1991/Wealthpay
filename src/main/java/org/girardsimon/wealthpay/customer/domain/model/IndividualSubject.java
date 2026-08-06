package org.girardsimon.wealthpay.customer.domain.model;

import java.util.Map;
import java.util.Set;

public record IndividualSubject(Nationalities nationalities, CountryCode residence)
    implements AdmissionSubject {

  public IndividualSubject {
    requireComplete(nationalities, residence);
  }

  // Extracted from the compact constructor so the mutation gate can see it: PITest's default
  // FRECORD filter suppresses every mutant inside a record's canonical constructor.
  private static void requireComplete(Nationalities nationalities, CountryCode residence) {
    if (nationalities == null || residence == null) {
      throw new IllegalArgumentException(
          "An individual subject requires nationalities and a country of residence");
    }
  }

  @Override
  public Map<ConnectingFactor, Set<CountryCode>> connections() {
    return Map.of(
        ConnectingFactor.NATIONALITY, nationalities.values(),
        ConnectingFactor.RESIDENCE, Set.of(residence));
  }

  @Override
  public ConnectingFactor establishmentFactor() {
    return ConnectingFactor.RESIDENCE;
  }
}
