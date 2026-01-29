package org.girardsimon.wealthpay.account.domain.exception;

public class InsufficientFundsException extends RuntimeException {

  public InsufficientFundsException() {
    super("Insufficient funds to complete the operation");
  }
}
