package org.girardsimon.wealthpay.account.domain.exception;

public class AccountHistoryNotFound extends RuntimeException {
    public AccountHistoryNotFound() {
        super("Account history not found");
    }
}
