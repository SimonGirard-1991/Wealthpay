package org.girardsimon.wealthpay.account.application.view;

import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;

public record AccountBalanceView(
    AccountId accountId, Money balance, Money reservedFunds, String status, long version) {}
