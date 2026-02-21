package org.girardsimon.wealthpay.account.domain.command;

import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public record CancelReservation(AccountId accountId, ReservationId reservationId) {
  public CancelReservation {
    if (accountId == null || reservationId == null) {
      throw new IllegalArgumentException("accountId and reservationId must not be null");
    }
  }
}
