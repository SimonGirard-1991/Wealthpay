package org.girardsimon.wealthpay.account.domain.exception;

import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;

public class TransactionIdConflictException extends RuntimeException {

  public TransactionIdConflictException(AccountId accountId, TransactionId transactionId) {
    super(
        "Transaction id conflict for account %s and transaction %s"
            .formatted(accountId, transactionId));
  }
}
