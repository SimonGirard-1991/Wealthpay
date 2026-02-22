package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.function.Function;
import org.girardsimon.wealthpay.account.api.generated.model.OpenAccountRequestDto;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.springframework.stereotype.Component;

@Component
public class OpenAccountDtoToDomainMapper implements Function<OpenAccountRequestDto, OpenAccount> {

  private final MoneyDtoToDomainMapper moneyDtoToDomainMapper;

  public OpenAccountDtoToDomainMapper(MoneyDtoToDomainMapper moneyDtoToDomainMapper) {
    this.moneyDtoToDomainMapper = moneyDtoToDomainMapper;
  }

  @Override
  public OpenAccount apply(OpenAccountRequestDto openAccountRequestDto) {

    return new OpenAccount(
        SupportedCurrency.valueOf(openAccountRequestDto.getAccountCurrency().name()),
        moneyDtoToDomainMapper.apply(
            openAccountRequestDto.getInitialAmount(),
            openAccountRequestDto.getInitialAmountCurrency()));
  }
}
