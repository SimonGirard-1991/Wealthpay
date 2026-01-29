package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import org.girardsimon.wealthpay.account.api.generated.model.AccountResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.AccountStatusDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.junit.jupiter.api.Test;

class AccountBalanceViewDomainToDtoMapperTest {

  AccountBalanceViewDomainToDtoMapper mapper = new AccountBalanceViewDomainToDtoMapper();

  @Test
  void map_account_balance_view_to_dto() {
    // Arrange
    AccountId accountId = AccountId.newId();
    Money balance = Money.of(BigDecimal.valueOf(100L), SupportedCurrency.USD);
    Money reserved = Money.of(BigDecimal.valueOf(50L), SupportedCurrency.USD);
    AccountBalanceView accountBalanceView =
        new AccountBalanceView(accountId, balance, reserved, "OPENED", 5L);

    // Act
    AccountResponseDto accountResponseDto = mapper.apply(accountBalanceView);

    // Assert
    assertAll(
        () -> assertThat(accountResponseDto.getId()).isEqualTo(accountId.id()),
        () -> assertThat(accountResponseDto.getBalanceAmount()).isEqualTo(balance.amount()),
        () -> assertThat(accountResponseDto.getReservedAmount()).isEqualTo(reserved.amount()),
        () -> assertThat(accountResponseDto.getCurrency()).isEqualTo(SupportedCurrencyDto.USD),
        () -> assertThat(accountResponseDto.getStatus()).isEqualTo(AccountStatusDto.OPENED));
  }
}
