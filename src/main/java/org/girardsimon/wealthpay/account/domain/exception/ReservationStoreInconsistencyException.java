package org.girardsimon.wealthpay.account.domain.exception;

/**
 * The processed-reservations store and the {@code Account} aggregate disagree about a reservation's
 * existence, though one transaction is meant to keep them in lockstep.
 *
 * <p>Not a customer-facing rejection like {@code InsufficientFundsException}: it is an internal
 * invariant breach, so it maps to 500 and buckets as {@code error} rather than {@code
 * invariant_violation}.
 */
public class ReservationStoreInconsistencyException extends RuntimeException {

  public ReservationStoreInconsistencyException(String message) {
    super(message);
  }
}
