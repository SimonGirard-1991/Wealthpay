package org.girardsimon.wealthpay.account.domain.event;

import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;

public record FundsReserved(
    AccountEventMeta meta, TransactionId transactionId, ReservationId reservationId, Money money)
    implements AccountEvent {}
