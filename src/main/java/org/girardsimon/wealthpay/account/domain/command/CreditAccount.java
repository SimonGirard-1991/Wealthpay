package org.girardsimon.wealthpay.account.domain.command;

import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;

public record CreditAccount(TransactionId transactionId, AccountId accountId, Money money)
    implements AccountTransaction {

  public CreditAccount {
    if (transactionId == null || accountId == null || money == null) {
      throw new IllegalArgumentException("transactionId, accountId and money must not be null");
    }
  }
}
