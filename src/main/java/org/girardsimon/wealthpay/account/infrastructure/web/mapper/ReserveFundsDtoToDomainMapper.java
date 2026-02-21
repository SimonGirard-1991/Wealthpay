package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsRequestDto;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.springframework.stereotype.Component;

@Component
public class ReserveFundsDtoToDomainMapper {

  public ReserveFunds apply(
      UUID accountId, UUID transactionId, ReserveFundsRequestDto reserveFundsRequestDto) {
    return new ReserveFunds(
        TransactionId.of(transactionId),
        AccountId.of(accountId),
        Money.of(
            reserveFundsRequestDto.getAmount(),
            SupportedCurrency.valueOf(reserveFundsRequestDto.getCurrency().name())));
  }
}
