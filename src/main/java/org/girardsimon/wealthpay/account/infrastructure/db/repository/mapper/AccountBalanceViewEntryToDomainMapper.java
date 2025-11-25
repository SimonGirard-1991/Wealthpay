package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;



import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.jooq.Record6;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import static org.girardsimon.wealthpay.account.jooq.tables.AccountBalanceView.ACCOUNT_BALANCE_VIEW;

@Component
public class AccountBalanceViewEntryToDomainMapper implements Function<Record6<UUID, BigDecimal, BigDecimal, String, String, Long>, AccountBalanceView> {

    @Override
    public AccountBalanceView apply(Record6<UUID, BigDecimal, BigDecimal, String, String, Long> entry) {
        SupportedCurrency currency = SupportedCurrency.fromValue(entry.get(ACCOUNT_BALANCE_VIEW.CURRENCY)
                .toUpperCase(Locale.US));

        BigDecimal balanceAmount = entry.get(ACCOUNT_BALANCE_VIEW.BALANCE);
        BigDecimal reservedAmount = entry.get(ACCOUNT_BALANCE_VIEW.RESERVED);

        return new AccountBalanceView(
                entry.get(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID),
                Money.of(balanceAmount, currency),
                Money.of(reservedAmount, currency),
                entry.get(ACCOUNT_BALANCE_VIEW.STATUS),
                entry.get(ACCOUNT_BALANCE_VIEW.VERSION)
        );
    }
}
