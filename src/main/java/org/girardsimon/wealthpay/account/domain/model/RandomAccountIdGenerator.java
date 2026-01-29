package org.girardsimon.wealthpay.account.domain.model;

import org.springframework.stereotype.Component;

@Component
public class RandomAccountIdGenerator implements AccountIdGenerator {

  @Override
  public AccountId newId() {
    return AccountId.newId();
  }
}
