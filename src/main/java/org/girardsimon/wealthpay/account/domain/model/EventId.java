package org.girardsimon.wealthpay.account.domain.model;

import java.util.UUID;

public record EventId(UUID id) {
  public EventId {
    if (id == null) {
      throw new IllegalArgumentException("Id must not be null");
    }
  }

  public static EventId of(UUID id) {
    return new EventId(id);
  }
}
