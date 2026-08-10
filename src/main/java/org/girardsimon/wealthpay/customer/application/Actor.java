package org.girardsimon.wealthpay.customer.application;

/** Who a status transition is attributed to on the audit trail. */
public record Actor(String value) {

  private static final int MAX_LENGTH = 255;

  /** Transitions with no human behind them. */
  public static final Actor SYSTEM = new Actor("SYSTEM");

  public Actor {
    requireIdentifiableActor(value);
  }

  private static void requireIdentifiableActor(String value) {
    // IllegalStateException throughout, not IllegalArgumentException: an actor comes from the
    // authenticated subject or from SYSTEM, never from a request body, and the global handler
    // renders the latter as a 400 blaming the caller. That holds once subjects arrive from a token
    // too - a principal that authenticates and then fails these checks means the filter chain
    // admitted it wrongly, which is a 401 upstream, not a 400 here.
    //
    // The column's CHECK only rejects the empty string, so a whitespace-only actor would otherwise
    // reach the audit trail and satisfy it.
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Audit actor must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalStateException("Audit actor must not exceed " + MAX_LENGTH + " characters");
    }
    if (!value.equals(value.strip()) || value.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalStateException(
          "Audit actor must not be padded or contain control characters");
    }
  }
}
