package org.girardsimon.wealthpay.customer.domain.model;

public record AdmittedDecision(long policyVersion) implements AdmissionDecision {

  public AdmittedDecision {
    AdmissionDecision.requireValidPolicyVersion(policyVersion);
  }
}
