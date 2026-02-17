package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.CreditAccountRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.junit.jupiter.api.Test;

class CreditAccountDtoToDomainMapperTest {

  CreditAccountDtoToDomainMapper mapper = new CreditAccountDtoToDomainMapper();

  @Test
  void shouldMapCreditAccountRequestDtoToCreditAccountCommand() {
    // Given
    UUID accountId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();
    CreditAccountRequestDto creditAccountRequestDto =
        new CreditAccountRequestDto()
            .currency(SupportedCurrencyDto.USD)
            .amount(BigDecimal.valueOf(100.50));

    // When
    CreditAccount creditAccount = mapper.apply(accountId, transactionId, creditAccountRequestDto);

    // Then
    assertAll(
        () -> assertThat(creditAccount.transactionId()).isEqualTo(TransactionId.of(transactionId)),
        () -> assertThat(creditAccount.accountId()).isEqualTo(AccountId.of(accountId)),
        () ->
            assertThat(creditAccount.money())
                .isEqualTo(Money.of(BigDecimal.valueOf(100.50), SupportedCurrency.USD)));
  }
}
