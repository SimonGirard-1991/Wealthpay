package org.girardsimon.wealthpay.account.domain.model;

import java.util.Map;

public record AccountSnapshot(
    AccountId accountId,
    SupportedCurrency currency,
    Money balance,
    AccountStatus status,
    Map<ReservationId, Money> reservations,
    long version) {
  public AccountSnapshot {
    reservations = Map.copyOf(reservations);
  }
}
