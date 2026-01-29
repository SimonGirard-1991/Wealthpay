package org.girardsimon.wealthpay.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MoneyTest {

  public static Stream<Arguments> invalidMoneys() {
    return Stream.of(
        Arguments.of(new BigDecimal("10"), null),
        Arguments.of(null, SupportedCurrency.USD),
        Arguments.of(null, null));
  }

  public static Stream<Arguments> amountAndExpectedRounding() {
    return Stream.of(
        Arguments.of(new BigDecimal("10.5011"), SupportedCurrency.USD, new BigDecimal("10.50")),
        Arguments.of(new BigDecimal("10.505"), SupportedCurrency.EUR, new BigDecimal("10.50")),
        Arguments.of(new BigDecimal("10.515"), SupportedCurrency.EUR, new BigDecimal("10.52")),
        Arguments.of(new BigDecimal("10.01"), SupportedCurrency.JPY, new BigDecimal("10")));
  }

  @ParameterizedTest
  @MethodSource("invalidMoneys")
  void check_money_consistency(BigDecimal amount, SupportedCurrency currency) {
    // Arrange ... Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> Money.of(amount, currency));
  }

  @ParameterizedTest
  @MethodSource("amountAndExpectedRounding")
  void should_normalize_amount_according_to_currency_fraction_digits(
      BigDecimal amount, SupportedCurrency currency, BigDecimal expectedAmountRounded) {
    // Act
    Money money = Money.of(amount, currency);

    // Assert
    assertThat(money.amount()).isEqualTo(expectedAmountRounded);
  }

  @Test
  void constructor_normalizes_amount_scale() {
    // Arrange
    BigDecimal amount = new BigDecimal("10.5011");
    SupportedCurrency currency = SupportedCurrency.USD;

    // Act
    Money money = new Money(amount, currency);

    // Assert
    assertThat(money.amount()).isEqualTo(new BigDecimal("10.50"));
  }
}
