package org.girardsimon.wealthpay.account.domain.command;

import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public record CancelReservation(AccountId accountId, ReservationId reservationId)
    implements ReservationCommand {
  public CancelReservation {
    if (accountId == null || reservationId == null) {
      throw new IllegalArgumentException("accountId and reservationId must not be null");
    }
  }
}
