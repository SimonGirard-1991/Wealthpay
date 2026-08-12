package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.function.Function;
import org.girardsimon.wealthpay.account.api.generated.model.OpenAccountRequestDto;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.springframework.stereotype.Component;

/**
 * The {@code @SuppressWarnings("java:S5128")} is deliberate: the DTO is already validated at the
 * HTTP boundary, and {@code @Valid} here would be a no-op on a class that is not
 * {@code @Validated}.
 */
@Component
public class OpenAccountDtoToDomainMapper implements Function<OpenAccountRequestDto, OpenAccount> {

  private final MoneyDtoToDomainMapper moneyDtoToDomainMapper;

  public OpenAccountDtoToDomainMapper(MoneyDtoToDomainMapper moneyDtoToDomainMapper) {
    this.moneyDtoToDomainMapper = moneyDtoToDomainMapper;
  }

  @Override
  public OpenAccount apply(
      @SuppressWarnings("java:S5128") OpenAccountRequestDto openAccountRequestDto) {

    return new OpenAccount(
        SupportedCurrency.valueOf(openAccountRequestDto.getAccountCurrency().name()),
        moneyDtoToDomainMapper.apply(
            openAccountRequestDto.getInitialAmount(),
            openAccountRequestDto.getInitialAmountCurrency()));
  }
}
