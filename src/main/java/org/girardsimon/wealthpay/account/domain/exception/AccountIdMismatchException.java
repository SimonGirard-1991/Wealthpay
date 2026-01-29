package org.girardsimon.wealthpay.account.domain.exception;

import org.girardsimon.wealthpay.account.domain.model.AccountId;

public class AccountIdMismatchException extends RuntimeException {

  public AccountIdMismatchException(AccountId accountId1, AccountId accountId2) {
    super("Account id mismatch: %s vs %s".formatted(accountId1, accountId2));
  }
}
