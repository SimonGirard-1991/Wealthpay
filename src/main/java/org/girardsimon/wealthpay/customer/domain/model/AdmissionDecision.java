package org.girardsimon.wealthpay.customer.domain.model;

/**
 * The outcome of evaluating a subject against the admission policy.
 *
 * <p>{@link RefusedDecision} must never reach the web layer. Withholding is a criminal-law duty
 * once suspicion has formed (AML tipping-off) and firm policy for an UNLICENSED refusal, where no
 * suspicion arises; the whole record is withheld either way.
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
