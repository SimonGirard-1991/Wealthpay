package org.girardsimon.wealthpay.account.domain.exception;

public class AccountHistoryNotFoundException extends RuntimeException {
  public AccountHistoryNotFoundException() {
    super("Account history not found");
  }
}
