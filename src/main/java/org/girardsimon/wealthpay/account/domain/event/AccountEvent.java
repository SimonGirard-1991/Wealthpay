package org.girardsimon.wealthpay.account.domain.event;

import java.time.Instant;
import org.girardsimon.wealthpay.account.domain.model.AccountId;

public sealed interface AccountEvent
    permits AccountOpened,
        FundsCredited,
        FundsDebited,
        FundsReserved,
        ReservationCancelled,
        AccountClosed,
        ReservationCaptured {

  AccountId accountId();

  Instant occurredAt();

  long version();
}
