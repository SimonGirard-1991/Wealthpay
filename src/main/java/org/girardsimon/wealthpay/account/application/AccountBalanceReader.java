package org.girardsimon.wealthpay.account.application;

import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.model.AccountId;

public interface AccountBalanceReader {
  AccountBalanceView getAccountBalance(AccountId accountId);
}
