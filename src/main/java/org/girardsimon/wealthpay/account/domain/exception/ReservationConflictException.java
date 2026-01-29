package org.girardsimon.wealthpay.account.domain.exception;

import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public class ReservationConflictException extends RuntimeException {
  public ReservationConflictException(
      ReservationId reservationId, Money existing, Money requested) {
    super(
        "Reservation already exists: "
            + reservationId
            + " with amount "
            + existing
            + " requested "
            + requested);
  }
}
