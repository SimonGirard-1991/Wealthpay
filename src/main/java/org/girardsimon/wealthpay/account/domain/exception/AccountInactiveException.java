package org.girardsimon.wealthpay.account.domain.exception;

public class AccountInactiveException extends RuntimeException {
  public AccountInactiveException() {
    super("Account is inactive");
  }
}
