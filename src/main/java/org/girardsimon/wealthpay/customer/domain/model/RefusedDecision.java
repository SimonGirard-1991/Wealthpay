package org.girardsimon.wealthpay.customer.domain.model;

import java.util.Comparator;
import java.util.List;

/**
 * A refusal, carrying every rule that fired rather than only the first. The deny quantifier is ANY
 * over a set, so several rules match routinely, and an investigation needs all of them.
 */
public record RefusedDecision(List<AdmissionMatch> matches, long policyVersion)
    implements AdmissionDecision {

  private static final Comparator<AdmissionMatch> MOST_SERIOUS_FIRST =
      Comparator.comparing(AdmissionMatch::restriction)
          .thenComparing(AdmissionMatch::connectingFactor)
          // CountryCode is not Comparable.
          .thenComparing(match -> match.triggeringCountry().value());

  public RefusedDecision {
    matches = ordered(matches);
    AdmissionDecision.requireValidPolicyVersion(policyVersion);
  }

  private static List<AdmissionMatch> ordered(List<AdmissionMatch> matches) {
    if (matches == null || matches.isEmpty()) {
      // IllegalStateException, not IllegalArgumentException, because the global handler maps the
      // latter to 400: an evaluator bug must not be reported to the client as a bad request, least
      // of all on the code path whose whole point is that refusals are not disclosed.
      throw new IllegalStateException("A refusal must carry at least one match");
    }
    // A one-element list never invokes the comparator, so sorting alone would let a lone null reach
    // primary().
    for (AdmissionMatch match : matches) {
      if (match == null) {
        throw new IllegalStateException("A refusal must not carry a null match");
      }
    }
    // Sorting is what makes the audit record reproducible: matches arrive from set iteration, whose
    // order the JDK salts per JVM launch, so two retries would otherwise record different rows.
    // distinct() because record equality is multiplicity-sensitive: a duplicated match
    // would yield two unequal audit records for one evaluation.
    return matches.stream().distinct().sorted(MOST_SERIOUS_FIRST).toList();
  }

  /** The most serious match: first in the ordering. Do not reach for max. */
  public AdmissionMatch primary() {
    return matches.getFirst();
  }
}
