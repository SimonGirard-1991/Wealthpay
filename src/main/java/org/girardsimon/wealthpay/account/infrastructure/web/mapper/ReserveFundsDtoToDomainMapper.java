package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsRequestDto;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.springframework.stereotype.Component;

/**
 * The {@code @SuppressWarnings("java:S5128")} is deliberate: the DTO is already validated at the
 * HTTP boundary, and {@code @Valid} here would be a no-op on a class that is not
 * {@code @Validated}.
 */
@Component
public class ReserveFundsDtoToDomainMapper {

  private final MoneyDtoToDomainMapper moneyDtoToDomainMapper;

  public ReserveFundsDtoToDomainMapper(MoneyDtoToDomainMapper moneyDtoToDomainMapper) {
    this.moneyDtoToDomainMapper = moneyDtoToDomainMapper;
  }

  public ReserveFunds apply(
      UUID accountId,
      UUID transactionId,
      @SuppressWarnings("java:S5128") ReserveFundsRequestDto reserveFundsRequestDto) {
    return new ReserveFunds(
        TransactionId.of(transactionId),
        AccountId.of(accountId),
        moneyDtoToDomainMapper.apply(
            reserveFundsRequestDto.getAmount(), reserveFundsRequestDto.getCurrency()));
  }
}
