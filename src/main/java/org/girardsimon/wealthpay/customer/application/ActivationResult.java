package org.girardsimon.wealthpay.customer.application;

import java.time.Instant;

/**
 * The outcome of an activation attempt, where {@code activatedAt} is always the instant the
 * customer was actually activated - this caller's clock reading only when {@code alreadyActive} is
 * false.
 */
public record ActivationResult(Instant activatedAt, boolean alreadyActive) {

  public ActivationResult {
    requireActivationInstant(activatedAt);
  }

  public static ActivationResult activated(Instant activatedAt) {
    return new ActivationResult(activatedAt, false);
  }

  public static ActivationResult alreadyActive(Instant activatedAt) {
    return new ActivationResult(activatedAt, true);
  }

  private static void requireActivationInstant(Instant activatedAt) {
    if (activatedAt == null) {
      throw new IllegalStateException("An activation result requires the activation instant");
    }
  }
}
