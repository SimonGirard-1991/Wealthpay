package org.girardsimon.wealthpay.customer.domain.exception;

/**
 * A persisted customer row violates an invariant the aggregate guarantees on write, so it cannot
 * have been produced by this code. Third tier: not a client error and never a 422.
 *
 * <p>Requires a dedicated 5xx mapping and a corruption alert; neither exists yet - it currently
 * reaches the catch-all handler, and no alert rule routes on it.
 *
 * <p>Messages must never echo row contents. The row is PII, and this exception reaches logs.
 */
public class CustomerRowCorruptException extends RuntimeException {

  public CustomerRowCorruptException(String message) {
    super(message);
  }
}
