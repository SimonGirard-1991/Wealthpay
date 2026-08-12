package org.girardsimon.wealthpay.account.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A posted monetary amount: legally payable, and carrying exactly the ISO 4217 fraction digits of
 * its currency.
 *
 * <p>Not for intermediate calculation - interest accrual, FX conversion, pricing. Those need more
 * precision than a posted amount carries, and rounding through Money at each step accumulates the
 * error. Convert once, at posting time.
 *
 * <p>Construction rescales under {@link RoundingMode#HALF_EVEN} rather than rejecting: an amount
 * arriving with more precision than the currency allows is rounded silently.
 */
public record Money(BigDecimal amount, SupportedCurrency currency) {

  public Money {
    if (amount == null || currency == null) {
      throw new IllegalArgumentException("amount and currency must not be null");
    }
    int defaultFractionDigits = currency.toJavaCurrency().getDefaultFractionDigits();
    amount = amount.setScale(defaultFractionDigits, RoundingMode.HALF_EVEN);
  }

  public static Money of(BigDecimal amount, SupportedCurrency currency) {
    return new Money(amount, currency);
  }

  public static Money zero(SupportedCurrency currency) {
    return Money.of(BigDecimal.ZERO, currency);
  }

  public boolean isNegativeOrZero() {
    return amount.signum() <= 0;
  }

  public boolean isStrictlyNegative() {
    return amount.signum() < 0;
  }

  public Money add(Money money) {
    ensureSameCurrency(money);
    return Money.of(this.amount.add(money.amount), this.currency);
  }

  public Money subtract(Money money) {
    ensureSameCurrency(money);
    return Money.of(this.amount.subtract(money.amount), this.currency);
  }

  public boolean isGreaterThan(Money money) {
    ensureSameCurrency(money);
    return this.amount.compareTo(money.amount) > 0;
  }

  public boolean isZero() {
    return this.amount.compareTo(BigDecimal.ZERO) == 0;
  }

  private void ensureSameCurrency(Money money) {
    if (!this.currency.equals(money.currency)) {
      throw new IllegalArgumentException(
          "Currencies mismatch: %s vs %s".formatted(this.currency, money.currency));
    }
  }
}
