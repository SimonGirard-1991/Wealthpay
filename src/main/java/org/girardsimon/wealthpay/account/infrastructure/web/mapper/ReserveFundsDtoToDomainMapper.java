package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsRequestDto;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.springframework.stereotype.Component;

/**
 * Note: the {@code @SuppressWarnings("java:S5128")} on the parameter is intentional. The request
 * DTO is already validated at the HTTP boundary by the OpenAPI-generated controller interface
 * (which declares {@code @Valid @RequestBody}); this mapper is a pure translation step, not a
 * validation boundary. Adding {@code @Valid} here would be a runtime no-op (the class is not
 * {@code @Validated}) and would mislead readers into thinking validation runs.
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
