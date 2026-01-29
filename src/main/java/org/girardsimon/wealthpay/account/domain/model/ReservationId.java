package org.girardsimon.wealthpay.account.domain.model;

import java.util.UUID;

public record ReservationId(UUID id) {
  public ReservationId {
    if (id == null) {
      throw new IllegalArgumentException("Id must not be null");
    }
  }

  public static ReservationId newId() {
    return new ReservationId(UUID.randomUUID());
  }

  public static ReservationId of(UUID id) {
    return new ReservationId(id);
  }
}
