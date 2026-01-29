package org.girardsimon.wealthpay.account.application;

import java.util.List;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.model.AccountId;

public interface AccountBalanceProjector {

  AccountBalanceView getAccountBalance(AccountId accountId);

  void project(List<AccountEvent> events);
}
