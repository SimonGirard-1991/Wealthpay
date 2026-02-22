package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.math.BigDecimal;
import java.util.function.BiFunction;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.springframework.stereotype.Component;

@Component
public class MoneyDtoToDomainMapper implements BiFunction<BigDecimal, SupportedCurrencyDto, Money> {

  @Override
  public Money apply(BigDecimal amount, SupportedCurrencyDto currency) {
    return Money.of(amount, SupportedCurrency.valueOf(currency.name()));
  }
}
