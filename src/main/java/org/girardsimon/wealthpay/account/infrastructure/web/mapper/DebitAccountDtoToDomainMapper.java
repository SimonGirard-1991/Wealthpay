package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.DebitAccountRequestDto;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.springframework.stereotype.Component;

/**
 * The {@code @SuppressWarnings("java:S5128")} is deliberate: the DTO is already validated at the
 * HTTP boundary, and {@code @Valid} here would be a no-op on a class that is not
 * {@code @Validated}.
 */
@Component
public class DebitAccountDtoToDomainMapper {

  private final MoneyDtoToDomainMapper moneyDtoToDomainMapper;

  public DebitAccountDtoToDomainMapper(MoneyDtoToDomainMapper moneyDtoToDomainMapper) {
    this.moneyDtoToDomainMapper = moneyDtoToDomainMapper;
  }

  public DebitAccount apply(
      UUID accountId,
      UUID transactionId,
      @SuppressWarnings("java:S5128") DebitAccountRequestDto debitAccountRequestDto) {
    return new DebitAccount(
        TransactionId.of(transactionId),
        AccountId.of(accountId),
        moneyDtoToDomainMapper.apply(
            debitAccountRequestDto.getAmount(), debitAccountRequestDto.getCurrency()));
  }
}
