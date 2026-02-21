package org.girardsimon.wealthpay.account.domain.exception;

import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public class ReservationAlreadyCanceledException extends RuntimeException {
  public ReservationAlreadyCanceledException(ReservationId reservationId) {
    super("Reservation already canceled: " + reservationId);
  }
}
