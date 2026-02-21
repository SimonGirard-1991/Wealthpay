package org.girardsimon.wealthpay.account.domain.event;

import java.time.Instant;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.EventId;

public sealed interface AccountEvent
    permits AccountOpened,
        FundsCredited,
        FundsDebited,
        FundsReserved,
        ReservationCanceled,
        AccountClosed,
        ReservationCaptured {
  AccountEventMeta meta();

  default EventId eventId() {
    return meta().eventId();
  }

  default AccountId accountId() {
    return meta().accountId();
  }

  default Instant occurredAt() {
    return meta().occurredAt();
  }

  default long version() {
    return meta().version();
  }
}
