package org.girardsimon.wealthpay.account.domain;

import org.girardsimon.wealthpay.account.domain.command.CloseAccount;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

class AccountClosingTest {

    @Test
    void closeAccount_emits_AccountClosed_event_and_set_status_to_CLOSED() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency currency = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
        AccountOpened opened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                currency,
                initialBalance
        );
        FundsDebited debited = new FundsDebited(
                TransactionId.newId(),
                accountId,
                Instant.now(),
                2L,
                initialBalance
        );
        List<AccountEvent> initEvents = List.of(opened, debited);
        Account account = Account.rehydrate(initEvents);
        CloseAccount closeAccount = new CloseAccount(accountId);

        // Act
        List<AccountEvent> closingEvents = account.handle(closeAccount, Instant.now());
        List<AccountEvent> allEvents = Stream.concat(initEvents.stream(), closingEvents.stream()).toList();
        Account accountAfterCredit = Account.rehydrate(allEvents);

        // Assert
        assertAll(
                () -> assertThat(allEvents).hasSize(3),
                () -> assertThat(allEvents.getLast()).isInstanceOf(AccountClosed.class),
                () -> assertThat(allEvents.getLast().version()).isEqualTo(3L),
                () -> assertThat(accountAfterCredit.getBalance().isAmountZero()).isTrue(),
                () -> assertThat(accountAfterCredit.getStatus()).isEqualTo(AccountStatus.CLOSED),
                () -> assertThat(accountAfterCredit.getVersion()).isEqualTo(3L)
        );
    }

    @Test
    void closeAccount_requires_same_id_as_account() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency currency = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
        AccountOpened opened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                currency,
                initialBalance
        );
        FundsDebited debited = new FundsDebited(
                TransactionId.newId(),
                accountId,
                Instant.now(),
                2L,
                initialBalance
        );
        Account account = Account.rehydrate(List.of(opened, debited));
        AccountId otherAccountId = AccountId.newId();
        CloseAccount closeAccount = new CloseAccount(otherAccountId);

        // Act ... Assert
        Instant occurredAt = Instant.now();
        assertThatExceptionOfType(AccountIdMismatchException.class)
                .isThrownBy(() -> account.handle(closeAccount, occurredAt));
    }

    @Test
    void closeAccount_requires_account_to_be_opened() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency currency = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
        AccountOpened opened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                currency,
                initialBalance
        );
        FundsDebited debited = new FundsDebited(
                TransactionId.newId(),
                accountId,
                Instant.now(),
                2L,
                initialBalance
        );
        AccountClosed closed = new AccountClosed(
                accountId,
                Instant.now(),
                3L
        );
        Account closedAccount = Account.rehydrate(List.of(opened, debited, closed));
        CloseAccount closeAccount = new CloseAccount(accountId);

        // Act ... Assert
        Instant occurredAt = Instant.now();
        assertThatExceptionOfType(AccountInactiveException.class)
                .isThrownBy(() -> closedAccount.handle(closeAccount, occurredAt));
    }
}
