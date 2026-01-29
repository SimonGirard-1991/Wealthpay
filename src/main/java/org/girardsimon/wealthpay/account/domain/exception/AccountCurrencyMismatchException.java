package org.girardsimon.wealthpay.account.domain.exception;

public class AccountCurrencyMismatchException extends RuntimeException {

  public AccountCurrencyMismatchException(String accountCurrency, String transactionCurrency) {
    super(
        "Account currency %s does not match initial balance currency %s"
            .formatted(accountCurrency, transactionCurrency));
  }
}
