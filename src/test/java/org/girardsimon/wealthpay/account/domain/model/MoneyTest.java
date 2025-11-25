package org.girardsimon.wealthpay.account.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MoneyTest {

    public static Stream<Arguments> invalidMoneys() {
        return Stream.of(
                Arguments.of(BigDecimal.valueOf(10L), null),
                Arguments.of(null, SupportedCurrency.USD),
                Arguments.of(null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidMoneys")
    void check_money_consistency(BigDecimal amount, SupportedCurrency currency) {
        // Arrange ... Act ... Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Money.of(amount, currency));
    }


    public static Stream<Arguments> amountAndExpectedRounding() {
        return Stream.of(
                Arguments.of(BigDecimal.valueOf(10.5011),
                        SupportedCurrency.USD, BigDecimal.valueOf(10.50).setScale(2, RoundingMode.HALF_EVEN)),
                Arguments.of(BigDecimal.valueOf(10.01),
                        SupportedCurrency.JPY, BigDecimal.valueOf(10).setScale(0, RoundingMode.HALF_EVEN))
        );
    }

    @ParameterizedTest
    @MethodSource("amountAndExpectedRounding")
    void should_normalize_amount_according_to_currency_fraction_digits(BigDecimal amount, SupportedCurrency currency, BigDecimal expectedAmountRounded) {
        // Act
        Money money = Money.of(amount, currency);

        // Assert
        assertThat(money.amount()).isEqualTo(expectedAmountRounded);
    }
}