package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.CreditAccountRequestDto;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
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
public class CreditAccountDtoToDomainMapper {

  private final MoneyDtoToDomainMapper moneyDtoToDomainMapper;

  public CreditAccountDtoToDomainMapper(MoneyDtoToDomainMapper moneyDtoToDomainMapper) {
    this.moneyDtoToDomainMapper = moneyDtoToDomainMapper;
  }

  public CreditAccount apply(
      UUID accountId,
      UUID transactionId,
      @SuppressWarnings("java:S5128") CreditAccountRequestDto creditAccountRequestDto) {
    return new CreditAccount(
        TransactionId.of(transactionId),
        AccountId.of(accountId),
        moneyDtoToDomainMapper.apply(
            creditAccountRequestDto.getAmount(), creditAccountRequestDto.getCurrency()));
  }
}
