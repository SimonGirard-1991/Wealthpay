package org.girardsimon.wealthpay.account.domain.model;

import java.util.UUID;

public record AccountId(UUID id) {
  public AccountId {
    if (id == null) {
      throw new IllegalArgumentException("Id must not be null");
    }
  }

  public static AccountId newId() {
    return new AccountId(UUID.randomUUID());
  }

  public static AccountId of(UUID id) {
    return new AccountId(id);
  }
}
