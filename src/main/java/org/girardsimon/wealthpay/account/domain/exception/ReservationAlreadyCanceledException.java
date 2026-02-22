package org.girardsimon.wealthpay.account.domain.exception;

public class ReservationAlreadyCanceledException extends RuntimeException {
  public ReservationAlreadyCanceledException(String message) {
    super(message);
  }
}
