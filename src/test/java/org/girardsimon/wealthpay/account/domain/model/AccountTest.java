package org.girardsimon.wealthpay.account.domain.model;

import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.exception.AccountHistoryNotFound;
import org.girardsimon.wealthpay.account.domain.exception.InvalidAccountEventStreamException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class AccountTest {

    @Test
    void rehydrate_requires_first_event_to_be_account_opened() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency currency = SupportedCurrency.USD;
        Money credit = Money.of(BigDecimal.valueOf(10L), currency);
        AccountEvent fakeEvent = new FundsCredited(
                TransactionId.newId(),
                accountId,
                Instant.now(),
                1L,
                credit
        );
        List<AccountEvent> history = List.of(fakeEvent);

        // Act ... Assert
        assertThatExceptionOfType(InvalidAccountEventStreamException.class)
                .isThrownBy(() -> Account.rehydrate(history));
    }

    @Test
    void rehydrate_requires_at_least_one_event() {
        // Arrange
        List<AccountEvent> history = Collections.emptyList();

        // Act ... Assert
        assertThatExceptionOfType(AccountHistoryNotFound.class)
                .isThrownBy(() -> Account.rehydrate(history));
    }

}