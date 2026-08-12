package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.CreditAccountRequestDto;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.springframework.stereotype.Component;

/**
 * The {@code @SuppressWarnings("java:S5128")} is deliberate: the DTO is already validated at the
 * HTTP boundary, and {@code @Valid} here would be a no-op on a class that is not
 * {@code @Validated}.
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
