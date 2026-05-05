package org.girardsimon.wealthpay.account.testsupport;

import java.util.UUID;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;

public class TestAccountIdGenerator implements AccountIdGenerator {
  @Override
  public AccountId newId() {
    return AccountId.of(UUID.randomUUID());
  }
}
