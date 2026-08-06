package org.girardsimon.wealthpay.customer.domain.model;

/**
 * The outcome of evaluating a subject against the admission policy.
 *
 * <p>{@link RefusedDecision} must never reach the web layer. Under the AML tipping-off regime,
 * telling a subject they matched a check is a criminal offense in most jurisdictions; whether a
 * sanctions or unlicensed-market match may be disclosed is regime-specific and unconfirmed, so the
 * whole record is withheld by default.
 */
public sealed interface AdmissionDecision permits AdmittedDecision, RefusedDecision {

  long policyVersion();

  static void requireValidPolicyVersion(long policyVersion) {
    if (policyVersion <= 0) {
      throw new IllegalStateException(
          "A decision must record the policy version it was made under");
    }
  }
}
