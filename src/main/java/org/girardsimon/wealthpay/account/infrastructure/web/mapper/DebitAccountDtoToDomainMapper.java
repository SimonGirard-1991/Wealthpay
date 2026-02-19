package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.DebitAccountRequestDto;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.springframework.stereotype.Component;

@Component
public class DebitAccountDtoToDomainMapper {

  public DebitAccount apply(
      UUID accountId, UUID transactionId, DebitAccountRequestDto debitAccountRequestDto) {
    return new DebitAccount(
        TransactionId.of(transactionId),
        AccountId.of(accountId),
        Money.of(
            debitAccountRequestDto.getAmount(),
            SupportedCurrency.valueOf(debitAccountRequestDto.getCurrency().name())));
  }
}
