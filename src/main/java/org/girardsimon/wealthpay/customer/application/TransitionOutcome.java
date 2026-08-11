package org.girardsimon.wealthpay.customer.application;

import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;

/** Which caller a conditional status transition belonged to. */
public enum TransitionOutcome {
  /** This caller took the sequence number and applied the status change. */
  APPLIED,
  /** Another caller had already taken the sequence number; nothing was written. */
  SUPERSEDED;

  /**
   * The conditional write reports a rowcount; what it means about who activated the customer is
   * this type's own rule, not the adapter's.
   */
  public static TransitionOutcome ofUpdatedRows(int updated) {
    return switch (updated) {
      case 0 -> SUPERSEDED;
      case 1 -> APPLIED;
      // The write predicates on the primary key, so no sanctioned path produces another count.
      default ->
          throw new CustomerRowCorruptException(
              "A status transition applied to " + updated + " customer rows");
    };
  }
}
