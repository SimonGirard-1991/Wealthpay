package org.girardsimon.wealthpay.account.domain.command;

import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public interface ReservationCommand {
  AccountId accountId();

  ReservationId reservationId();
}
