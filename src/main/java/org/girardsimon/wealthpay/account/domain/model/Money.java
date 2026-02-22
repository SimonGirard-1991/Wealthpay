package org.girardsimon.wealthpay.account.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable value object representing a posted monetary amount in a specific currency.
 *
 * <p>A Money value is legally payable and must respect ISO 4217 fraction digits. It is suitable for
 * balances, transactions, statements, and settlements.
 *
 * <p>This type must NOT be used for intermediate financial calculations (e.g., interest accruals,
 * FX conversions, price computations). Such calculations require higher precision and must be
 * converted to Money only at posting time.
 *
 * <p>This class guarantees the following invariants:
 *
 * <ul>
 *   <li>Amount is never null
 *   <li>Currency is never null
 *   <li>Amount scale matches the currency's default fraction digits (e.g., 2 for USD, 0 for JPY)
 * </ul>
 *
 * <p>Amounts are normalized using {@link RoundingMode#HALF_EVEN} (banker's rounding) to minimize
 * cumulative rounding errors in financial calculations.
 *
 * <p><b>Usage:</b> Prefer the factory method {@link #of(BigDecimal, SupportedCurrency)} over direct
 * constructor invocation.
 *
 * <pre>{@code
 * Money price = Money.of(new BigDecimal("19.99"), SupportedCurrency.USD);
 * Money total = price.add(Money.of(new BigDecimal("5.00"), SupportedCurrency.USD));
 * }</pre>
 *
 * @param amount the monetary amount, normalized to currency's fraction digits
 * @param currency the currency of this money instance
 */
public record Money(BigDecimal amount, SupportedCurrency currency) {

  /** Normalizes amount scale to currency's fraction digits using banker's rounding. */
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
