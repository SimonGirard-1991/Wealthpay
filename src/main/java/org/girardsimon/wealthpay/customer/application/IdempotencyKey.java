package org.girardsimon.wealthpay.customer.application;

/**
 * A client-supplied key identifying one registration attempt.
 *
 * <p>Taken verbatim: {@code "abc "} and {@code "abc"} would be two distinct registrations, so
 * surrounding whitespace is rejected rather than trimmed away behind the client's back.
 */
public record IdempotencyKey(String value) {

  // The conventional cap, and what clients that generate keys already assume.
  private static final int MAX_LENGTH = 255;

  public IdempotencyKey {
    requireUsableKey(value);
  }

  private static void requireUsableKey(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Idempotency key must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Idempotency key must not exceed " + MAX_LENGTH + " characters");
    }
    if (!value.equals(value.strip())) {
      throw new IllegalArgumentException(
          "Idempotency key must not have leading or trailing whitespace");
    }
    // This value is the correlation handle between a decision and a registration, so it reaches the
    // logs. Control characters cover the newline that would forge an audit line in a plain-text
    // appender; they do not cover U+2028, which only a JS-based log viewer treats as a line break.
    if (value.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Idempotency key must not contain control characters");
    }
  }
}
