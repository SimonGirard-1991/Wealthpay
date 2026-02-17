package org.girardsimon.wealthpay.account.domain.command;

import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;

public record DebitAccount(TransactionId transactionId, AccountId accountId, Money money) {

  public DebitAccount {
    if (transactionId == null || accountId == null || money == null) {
      throw new IllegalArgumentException("transactionId, accountId and money must not be null");
    }
  }
}
