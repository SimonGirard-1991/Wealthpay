package org.girardsimon.wealthpay.account.domain.exception;

import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public class ReservationAlreadyCapturedException extends RuntimeException {

  public ReservationAlreadyCapturedException(ReservationId reservationId) {
    super("Reservation already captured: " + reservationId);
  }
}
