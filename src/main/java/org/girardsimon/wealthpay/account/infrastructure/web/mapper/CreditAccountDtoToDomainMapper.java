package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.CreditAccountRequestDto;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.springframework.stereotype.Component;

@Component
public class CreditAccountDtoToDomainMapper {

  public CreditAccount apply(
      UUID accountId, UUID transactionId, CreditAccountRequestDto creditAccountRequestDto) {
    return new CreditAccount(
        TransactionId.of(transactionId),
        AccountId.of(accountId),
        Money.of(
            creditAccountRequestDto.getAmount(),
            SupportedCurrency.valueOf(creditAccountRequestDto.getCurrency().name())));
  }
}
