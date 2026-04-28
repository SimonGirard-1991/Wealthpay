package org.girardsimon.wealthpay.account.infrastructure.id;

import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.springframework.stereotype.Component;

@Component
public class RandomAccountIdGenerator implements AccountIdGenerator {

  @Override
  public AccountId newId() {
    return AccountId.newId();
  }
}
