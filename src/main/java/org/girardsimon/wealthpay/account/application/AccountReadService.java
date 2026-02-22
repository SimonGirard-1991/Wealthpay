package org.girardsimon.wealthpay.account.application;

import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountReadService {

  private final AccountBalanceReader accountBalanceReader;

  public AccountReadService(AccountBalanceReader accountBalanceReader) {
    this.accountBalanceReader = accountBalanceReader;
  }

  @Transactional(readOnly = true)
  public AccountBalanceView getAccountBalance(AccountId accountId) {
    return accountBalanceReader.getAccountBalance(accountId);
  }
}
