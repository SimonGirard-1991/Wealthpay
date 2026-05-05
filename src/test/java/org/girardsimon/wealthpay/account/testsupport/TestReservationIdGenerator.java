package org.girardsimon.wealthpay.account.testsupport;

import java.util.UUID;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.ReservationIdGenerator;

public class TestReservationIdGenerator implements ReservationIdGenerator {
  @Override
  public ReservationId newId() {
    return ReservationId.of(UUID.randomUUID());
  }
}
