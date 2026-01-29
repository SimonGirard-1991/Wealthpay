package org.girardsimon.wealthpay.account.domain.event;

import java.time.Instant;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public record ReservationCaptured(
    AccountId accountId, ReservationId reservationId, Money money, long version, Instant occurredAt)
    implements AccountEvent {}
