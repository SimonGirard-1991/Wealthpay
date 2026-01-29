package org.girardsimon.wealthpay.account.domain.exception;

import org.girardsimon.wealthpay.account.domain.model.Money;

public class InvalidInitialBalanceException extends RuntimeException {

  public InvalidInitialBalanceException(Money money) {
    super("Initial balance must be strictly positive, got " + money);
  }
}
