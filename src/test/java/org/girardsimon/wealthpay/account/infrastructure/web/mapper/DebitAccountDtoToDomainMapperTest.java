package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.DebitAccountRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.junit.jupiter.api.Test;

class DebitAccountDtoToDomainMapperTest {

  DebitAccountDtoToDomainMapper mapper = new DebitAccountDtoToDomainMapper();

  @Test
  void shouldMapDebitAccountRequestDtoToDebitAccountCommand() {
    // Arrange
    UUID accountId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();
    DebitAccountRequestDto debitAccountRequestDto =
        new DebitAccountRequestDto()
            .currency(SupportedCurrencyDto.USD)
            .amount(BigDecimal.valueOf(100.50));

    // Act
    DebitAccount debitAccount = mapper.apply(accountId, transactionId, debitAccountRequestDto);

    // Assert
    assertAll(
        () -> assertThat(debitAccount.transactionId()).isEqualTo(TransactionId.of(transactionId)),
        () -> assertThat(debitAccount.accountId()).isEqualTo(AccountId.of(accountId)),
        () ->
            assertThat(debitAccount.money())
                .isEqualTo(Money.of(BigDecimal.valueOf(100.50), SupportedCurrency.USD)));
  }
}
