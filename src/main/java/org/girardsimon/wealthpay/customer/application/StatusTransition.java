package org.girardsimon.wealthpay.customer.application;

import org.girardsimon.wealthpay.customer.domain.model.CustomerStatus;

/**
 * The two ends of a status change, carrying the invariant that they differ.
 *
 * <p>A reversed pair still compiles. Build it with {@link LoadedCustomer#transitionTo}, which takes
 * the source from the loaded snapshot instead of from the caller.
 */
public record StatusTransition(CustomerStatus from, CustomerStatus to) {

  public StatusTransition {
    requireDistinctEnds(from, to);
  }

  private static void requireDistinctEnds(CustomerStatus from, CustomerStatus to) {
    // IllegalStateException, not IllegalArgumentException: both ends come from the loaded snapshot
    // and the use case, never from a request, so the global handler must not render this as a 400.
    if (from == null || to == null) {
      throw new IllegalStateException("A status transition requires both a source and a target");
    }
    if (from == to) {
      throw new IllegalStateException("A status transition must change the status");
    }
  }
}
