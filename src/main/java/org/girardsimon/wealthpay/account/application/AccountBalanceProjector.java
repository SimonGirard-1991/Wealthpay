package org.girardsimon.wealthpay.account.application;

import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.model.AccountId;

import java.util.List;

public interface AccountBalanceProjector {

    AccountBalanceView getAccountBalance(AccountId accountId);

    void project(List<AccountEvent> events);
}
