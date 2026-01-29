package org.girardsimon.wealthpay.account.domain.command;

import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;

public record OpenAccount(SupportedCurrency accountCurrency, Money initialBalance) {

  public OpenAccount {
    if (accountCurrency == null || initialBalance == null) {
      throw new IllegalArgumentException("accountCurrency and initialBalance must not be null");
    }
  }
}
