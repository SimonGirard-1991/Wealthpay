package org.girardsimon.wealthpay.customer.domain.model;

import java.util.UUID;

public record CustomerId(UUID id) {

  public CustomerId {
    requirePresent(id);
  }

  private static void requirePresent(UUID id) {
    if (id == null) {
      throw new IllegalArgumentException("Id must not be null");
    }
  }

  public static CustomerId of(UUID id) {
    return new CustomerId(id);
  }
}
