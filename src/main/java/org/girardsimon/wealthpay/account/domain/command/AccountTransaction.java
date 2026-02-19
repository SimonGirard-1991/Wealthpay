package org.girardsimon.wealthpay.account.domain.command;

import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;

public interface AccountTransaction {
  TransactionId transactionId();

  AccountId accountId();
}
