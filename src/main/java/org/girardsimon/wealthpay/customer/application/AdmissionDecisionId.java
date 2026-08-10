package org.girardsimon.wealthpay.customer.application;

import java.util.UUID;

/**
 * Identifies a persisted admission decision, so that the transaction which records the decision and
 * the later one which anchors it to a customer can be wired together.
 */
public record AdmissionDecisionId(UUID id) {

  public AdmissionDecisionId {
    requirePresent(id);
  }

  private static void requirePresent(UUID id) {
    // IllegalStateException, not IllegalArgumentException: this comes back from the recorder, never
    // from a request, and the global handler renders the latter as a 400 blaming the caller.
    if (id == null) {
      throw new IllegalStateException("Admission decision id must not be null");
    }
  }
}
