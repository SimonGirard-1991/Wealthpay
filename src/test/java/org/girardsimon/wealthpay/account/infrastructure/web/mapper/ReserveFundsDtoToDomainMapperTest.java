package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.junit.jupiter.api.Test;

class ReserveFundsDtoToDomainMapperTest {

  ReserveFundsDtoToDomainMapper mapper =
      new ReserveFundsDtoToDomainMapper(new MoneyDtoToDomainMapper());

  @Test
  void map_reserve_funds_dto_to_command() {
    // Arrange
    UUID accountId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();
    ReserveFundsRequestDto reserveFundsRequestDto =
        new ReserveFundsRequestDto()
            .currency(SupportedCurrencyDto.USD)
            .amount(BigDecimal.valueOf(100.50));

    // Act
    ReserveFunds reserveFunds = mapper.apply(accountId, transactionId, reserveFundsRequestDto);

    // Assert
    assertAll(
        () -> assertThat(reserveFunds.accountId()).isEqualTo(AccountId.of(accountId)),
        () -> assertThat(reserveFunds.transactionId()).isEqualTo(TransactionId.of(transactionId)),
        () ->
            assertThat(reserveFunds.money())
                .isEqualTo(Money.of(BigDecimal.valueOf(100.50), SupportedCurrency.USD)));
  }
}
