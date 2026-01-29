package org.girardsimon.wealthpay.account.domain.exception;

import org.girardsimon.wealthpay.account.domain.model.Money;

public class AmountMustBePositiveException extends RuntimeException {

  public AmountMustBePositiveException(Money money) {
    super("Amount must be strictly positive, got " + money);
  }
}
