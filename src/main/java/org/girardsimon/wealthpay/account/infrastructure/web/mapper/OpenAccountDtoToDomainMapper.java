package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.function.Function;
import org.girardsimon.wealthpay.account.api.generated.model.OpenAccountRequestDto;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.springframework.stereotype.Component;

/**
 * Note: the {@code @SuppressWarnings("java:S5128")} on the parameter is intentional. The request
 * DTO is already validated at the HTTP boundary by the OpenAPI-generated controller interface
 * (which declares {@code @Valid @RequestBody}); this mapper is a pure translation step, not a
 * validation boundary. Adding {@code @Valid} here would be a runtime no-op (the class is not
 * {@code @Validated}) and would mislead readers into thinking validation runs.
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
