package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.girardsimon.wealthpay.account.api.generated.model.OpenAccountRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.junit.jupiter.api.Test;

class OpenAccountDtoToDomainMapperTest {

  OpenAccountDtoToDomainMapper mapper =
      new OpenAccountDtoToDomainMapper(new MoneyDtoToDomainMapper());

  @Test
  void map_open_account_dto_to_command() {
    // Arrange
    BigDecimal initialAmount = BigDecimal.valueOf(100);
    SupportedCurrencyDto chfDto = SupportedCurrencyDto.CHF;
    OpenAccountRequestDto openAccountRequestDto =
        new OpenAccountRequestDto()
            .accountCurrency(chfDto)
            .initialAmount(initialAmount)
            .initialAmountCurrency(chfDto);

    // Act
    OpenAccount openAccount = mapper.apply(openAccountRequestDto);

    // Assert
    SupportedCurrency chf = SupportedCurrency.CHF;
    assertAll(
        () -> assertThat(openAccount.accountCurrency()).isEqualTo(chf),
        () ->
            assertThat(openAccount.initialBalance().amount())
                .isEqualTo(initialAmount.setScale(2, RoundingMode.HALF_EVEN)),
        () -> assertThat(openAccount.initialBalance().currency()).isEqualTo(chf));
  }
}
