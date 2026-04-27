package org.girardsimon.wealthpay.account.domain.exception;

/**
 * Thrown when the processed-reservations store and the {@code Account} aggregate disagree about a
 * reservation's existence — a "should never happen" inconsistency between two stores that are meant
 * to be kept in lockstep by the same transaction.
 *
 * <p>This is not a domain-rule violation in the same sense as {@code InsufficientFundsException}
 * (which is a customer-facing 4xx outcome) — it is an internal invariant breach that warrants
 * investigation. The HTTP layer maps it to 500 with {@code log.error}; the metrics aspect
 * accordingly buckets it as {@code error} (alongside infrastructure failures and {@code
 * InvalidAccountEventStreamException}), so that an error-rate alert built on either surface stays
 * consistent with the HTTP 5xx paging signal.
 */
public class ReservationStoreInconsistencyException extends RuntimeException {

  public ReservationStoreInconsistencyException(String message) {
    super(message);
  }
}
