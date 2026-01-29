package org.girardsimon.wealthpay.account.domain.exception;

import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public class ReservationNotFoundException extends RuntimeException {
  public ReservationNotFoundException(ReservationId reservationId) {
    super("Reservation not found: " + reservationId);
  }
}
