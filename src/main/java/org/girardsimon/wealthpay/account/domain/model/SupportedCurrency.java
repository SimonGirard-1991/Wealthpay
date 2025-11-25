package org.girardsimon.wealthpay.account.domain.model;

import org.girardsimon.wealthpay.account.domain.exception.UnsupportedCurrencyException;

import java.util.Currency;

public enum SupportedCurrency {
    USD,
    AED,
    AUD,
    CAD,
    CHF,
    CNH,
    CZK,
    DKK,
    EUR,
    GBP,
    HKD,
    HUF,
    ILS,
    JPY,
    KRW,
    MXN,
    MYR,
    NOK,
    NZD,
    PLN,
    SAR,
    SEK,
    SGD,
    TRY,
    TWD,
    ZAR;

    public Currency toJavaCurrency() {
        return Currency.getInstance(name());
    }

    public static SupportedCurrency fromValue(String currency) {
        try {
            return SupportedCurrency.valueOf(currency);
        } catch (IllegalArgumentException _) {
            throw new UnsupportedCurrencyException(currency);
        }
    }
}
