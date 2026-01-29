package org.girardsimon.wealthpay.account.domain.event;

import java.time.Instant;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;

public record FundsReserved(
    AccountId accountId, Instant occurredAt, long version, ReservationId reservationId, Money money)
    implements AccountEvent {}
