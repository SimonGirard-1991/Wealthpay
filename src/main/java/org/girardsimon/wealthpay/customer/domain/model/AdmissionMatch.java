package org.girardsimon.wealthpay.customer.domain.model;

/**
 * One rule, and - when it fires against a subject - the match it produces. A deny-side rule and the
 * match recorded for it are the same triple, so the same type serves both: there is no mapping step
 * between "RU is sanctioned by nationality" and the audit row, and therefore no way to transcribe
 * it as RU/RESIDENCE by mistake.
 */
public record AdmissionMatch(
    Restriction restriction, ConnectingFactor connectingFactor, CountryCode triggeringCountry) {

  public AdmissionMatch {
    requireComplete(restriction, connectingFactor, triggeringCountry);
  }

  private static void requireComplete(
      Restriction restriction, ConnectingFactor connectingFactor, CountryCode triggeringCountry) {
    if (restriction == null || connectingFactor == null || triggeringCountry == null) {
      throw new IllegalArgumentException(
          "An admission match requires a restriction, a connecting factor and a country");
    }
  }
}
