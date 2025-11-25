package org.girardsimon.wealthpay.account.domain.exception;

public class UnsupportedCurrencyException extends RuntimeException {
    public UnsupportedCurrencyException(String unsupportedCurrency) {
        super("Currency " + unsupportedCurrency + " is not supported");
    }
}
