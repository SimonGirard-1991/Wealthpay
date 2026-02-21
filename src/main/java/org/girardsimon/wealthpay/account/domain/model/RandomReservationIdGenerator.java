package org.girardsimon.wealthpay.account.domain.model;

import org.springframework.stereotype.Component;

@Component
public class RandomReservationIdGenerator implements ReservationIdGenerator {
  @Override
  public ReservationId newId() {
    return ReservationId.newId();
  }
}
