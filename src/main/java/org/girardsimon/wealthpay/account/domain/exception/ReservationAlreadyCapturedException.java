package org.girardsimon.wealthpay.account.domain.exception;

public class ReservationAlreadyCapturedException extends RuntimeException {

  public ReservationAlreadyCapturedException(String message) {
    super(message);
  }
}
