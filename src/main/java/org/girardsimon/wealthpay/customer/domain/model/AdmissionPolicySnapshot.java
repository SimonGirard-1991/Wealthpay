package org.girardsimon.wealthpay.customer.domain.model;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.girardsimon.wealthpay.customer.domain.exception.AdmissionPolicyCorruptException;

/**
 * The admission policy as it stood at one instant: both rule sets plus the version they were read
 * at, captured in a single read, so the rules and the version cannot come from different snapshots.
 *
 * <p>That is narrower than "a decision documents the policy applied to it". Nothing forces an edit
 * to the policy tables to advance the version - the migration that creates them states the
 * convention and enforces only that the counter never moves backwards - so two decisions citing one
 * version may have been evaluated against different rules.
 *
 * <p>The two sides have opposite polarity, structurally rather than by a flag. {@code restrictions}
 * is a deny-list; {@code licences} is an allowlist. Licensing must fail closed - forgetting a
 * country declines business, which is recoverable, whereas a deny-list would serve a market we hold
 * no license in, which is a regulatory breach.
 */
public record AdmissionPolicySnapshot(
    Set<AdmissionMatch> restrictions,
    Map<ConnectingFactor, Set<CountryCode>> licences,
    long policyVersion) {

  public AdmissionPolicySnapshot {
    restrictions = validRestrictions(restrictions);
    licences = validLicences(licences);
    requireValidPolicyVersion(policyVersion);
  }

  private static Set<AdmissionMatch> validRestrictions(Set<AdmissionMatch> restrictions) {
    if (restrictions == null) {
      throw new AdmissionPolicyCorruptException("Admission policy has no restriction rules");
    }
    // An explicit loop, not contains(null): immutable sets reject a null query with an NPE.
    for (AdmissionMatch restriction : restrictions) {
      if (restriction == null) {
        throw new AdmissionPolicyCorruptException("Admission policy has a null restriction rule");
      }
      // UNLICENSED has allowlist polarity, so it is only ever produced by the licence check.
      // Finding
      // one seeded on the deny side means the two tables have been crossed.
      if (restriction.restriction() == Restriction.UNLICENSED) {
        throw new AdmissionPolicyCorruptException(
            "Admission policy seeds UNLICENSED as a deny-side rule");
      }
    }
    return Set.copyOf(restrictions);
  }

  private static Map<ConnectingFactor, Set<CountryCode>> validLicences(
      Map<ConnectingFactor, Set<CountryCode>> licences) {
    if (licences == null) {
      throw new AdmissionPolicyCorruptException("Admission policy has no license rules");
    }
    // Naming the offending factor matters: a corrupt policy row refuses every registration in the
    // system, and the generic refusal body gives the operator nothing to go on.
    licences.forEach(
        (factor, countries) -> {
          if (factor == null || countries == null) {
            throw new AdmissionPolicyCorruptException(
                "Admission policy has a corrupt license rule for factor " + factor);
          }
          for (CountryCode country : countries) {
            if (country == null) {
              throw new AdmissionPolicyCorruptException(
                  "Admission policy has a null licensed country for factor " + factor);
            }
          }
        });
    // Map.copyOf would leave the value sets shared with the caller.
    return licences.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
  }

  private static void requireValidPolicyVersion(long policyVersion) {
    if (policyVersion <= 0) {
      throw new AdmissionPolicyCorruptException("Admission policy must carry a positive version");
    }
  }

  /** Countries we are licensed in for a factor. An absent factor means licensed nowhere. */
  public Set<CountryCode> licensedFor(ConnectingFactor factor) {
    return licences.getOrDefault(factor, Set.of());
  }
}
