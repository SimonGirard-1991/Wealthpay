package org.girardsimon.wealthpay.account.domain.model;

import java.util.Currency;
import org.girardsimon.wealthpay.account.domain.exception.UnsupportedCurrencyException;

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
