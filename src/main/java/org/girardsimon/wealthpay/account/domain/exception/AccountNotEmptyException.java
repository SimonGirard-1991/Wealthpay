package org.girardsimon.wealthpay.account.domain.exception;

public class AccountNotEmptyException extends RuntimeException {

  public AccountNotEmptyException() {
    super("Account is not empty");
  }
}
