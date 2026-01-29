package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.function.Function;
import org.girardsimon.wealthpay.account.api.generated.model.AccountResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.AccountStatusDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.springframework.stereotype.Component;

@Component
public class AccountBalanceViewDomainToDtoMapper
    implements Function<AccountBalanceView, AccountResponseDto> {
  @Override
  public AccountResponseDto apply(AccountBalanceView accountBalanceView) {
    Money balance = accountBalanceView.balance();
    SupportedCurrency currency = balance.currency();
    return new AccountResponseDto()
        .id(accountBalanceView.accountId().id())
        .balanceAmount(balance.amount())
        .reservedAmount(accountBalanceView.reservedFunds().amount())
        .currency(SupportedCurrencyDto.valueOf(currency.name()))
        .status(AccountStatusDto.valueOf(accountBalanceView.status()));
  }
}
