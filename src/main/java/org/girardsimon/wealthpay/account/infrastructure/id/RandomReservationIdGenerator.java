package org.girardsimon.wealthpay.account.infrastructure.id;

import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.ReservationIdGenerator;
import org.springframework.stereotype.Component;

@Component
public class RandomReservationIdGenerator implements ReservationIdGenerator {
  @Override
  public ReservationId newId() {
    return ReservationId.newId();
  }
}
