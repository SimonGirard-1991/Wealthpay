package org.girardsimon.wealthpay.account.domain;

import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.exception.AccountCurrencyMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.exception.AmountMustBePositiveException;
import org.girardsimon.wealthpay.account.domain.exception.InsufficientFundsException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationConflictException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
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

class ReserveFundsTest {

    @Test
    void reserveFunds_emits_FundsReserved_event_and_update_account_reservations() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency currency = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(15L), currency);
        OpenAccount openAccount = new OpenAccount(currency, initialBalance);
        Money reservationAmount = Money.of(BigDecimal.valueOf(5L), currency);
        ReservationId reservationId = ReservationId.newId();
        ReserveFunds reserveFunds = new ReserveFunds(accountId, reservationId, reservationAmount);

        // Act
        List<AccountEvent> openingEvents = Account.handle(openAccount, accountId, 1L, Instant.now());
        Account account = Account.rehydrate(openingEvents);
        List<AccountEvent> reserveFundsEvents = account.handle(reserveFunds, Instant.now());
        List<AccountEvent> allEvents = Stream.concat(openingEvents.stream(), reserveFundsEvents.stream()).toList();
        Account accountAfterReservation = Account.rehydrate(allEvents);

        // Assert
        Money expectedBalance = Money.of(BigDecimal.valueOf(10L), currency); 
        assertAll(
                () -> assertThat(allEvents).hasSize(2),
                () -> assertThat(allEvents.getLast()).isInstanceOf(FundsReserved.class),
                () -> assertThat(allEvents.getLast().version()).isEqualTo(2L),
                () -> assertThat(accountAfterReservation.getBalance()).isEqualTo(initialBalance),
                () -> assertThat(accountAfterReservation.getAvailableBalance()).isEqualTo(expectedBalance),
                () -> assertThat(accountAfterReservation.getReservations()).containsEntry(reservationId, reservationAmount),
                () -> assertThat(accountAfterReservation.getStatus()).isEqualTo(AccountStatus.OPENED),
                () -> assertThat(accountAfterReservation.getVersion()).isEqualTo(2L)
        );
    }

    @Test
    void reserveFunds_requires_same_currency_as_account() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
        AccountOpened accountOpened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                usd,
                initialBalance
        );
        Account account = Account.rehydrate(List.of(accountOpened));
        SupportedCurrency chf = SupportedCurrency.CHF;
        Money reservedAmount = Money.of(BigDecimal.valueOf(5L), chf);
        ReservationId reservationId = ReservationId.newId();
        ReserveFunds reserveFunds = new ReserveFunds(accountId, reservationId, reservedAmount);

        // Act ... Assert
        Instant occurredAt = Instant.now();
        assertThatExceptionOfType(AccountCurrencyMismatchException.class)
                .isThrownBy(() -> account.handle(reserveFunds, occurredAt));
    }

    @Test
    void reserveFunds_requires_same_id_as_account() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
        AccountOpened accountOpened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                usd,
                initialBalance
        );
        Account account = Account.rehydrate(List.of(accountOpened));
        Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
        AccountId otherAccountId = AccountId.newId();
        ReservationId reservationId = ReservationId.newId();
        ReserveFunds reserveFunds = new ReserveFunds(otherAccountId, reservationId, reservedAmount);

        // Act ... Assert
        Instant occurredAt = Instant.now();
        assertThatExceptionOfType(AccountIdMismatchException.class)
                .isThrownBy(() -> account.handle(reserveFunds, occurredAt));
    }

    @Test
    void reserveFunds_requires_strictly_positive_amount() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
        AccountOpened accountOpened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                usd,
                initialBalance
        );
        Account account = Account.rehydrate(List.of(accountOpened));
        Money reservedAmount = Money.of(BigDecimal.valueOf(-5L), usd);
        ReservationId reservationId = ReservationId.newId();
        ReserveFunds reserveFunds = new ReserveFunds(accountId, reservationId, reservedAmount);

        // Act ... Assert
        Instant occurredAt = Instant.now();
        assertThatExceptionOfType(AmountMustBePositiveException.class)
                .isThrownBy(() -> account.handle(reserveFunds, occurredAt));
    }

    @Test
    void reserveFunds_requires_account_to_be_opened() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
        AccountOpened opened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                usd,
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
        Money reservedAmount = Money.of(BigDecimal.valueOf(10L), usd);
        ReservationId reservationId = ReservationId.newId();
        ReserveFunds reserveFunds = new ReserveFunds(accountId, reservationId, reservedAmount);

        // Act + Assert
        Instant occurredAt = Instant.now();
        assertThatExceptionOfType(AccountInactiveException.class)
                .isThrownBy(() -> closedAccount.handle(reserveFunds, occurredAt));
    }

    @Test
    void reserveFunds_requires_reserved_amount_to_be_less_than_account_available_balance() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(100L), usd);
        AccountOpened opened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                usd,
                initialBalance
        );
        Money firstReservedAmount = Money.of(BigDecimal.valueOf(60L), usd);
        FundsReserved fundsReserved = new FundsReserved(
                accountId,
                Instant.now(),
                2L,
                ReservationId.newId(),
                firstReservedAmount
        );
        Account account = Account.rehydrate(List.of(opened, fundsReserved));
        Money reservedAmount = Money.of(BigDecimal.valueOf(50L), usd); // 50 > 100 - 60 = 40 available balance
        ReservationId reservationId = ReservationId.newId();
        ReserveFunds reserveFunds = new ReserveFunds(accountId, reservationId, reservedAmount);

        // Act + Assert
        Instant occurredAt = Instant.now();
        assertThatExceptionOfType(InsufficientFundsException.class)
                .isThrownBy(() -> account.handle(reserveFunds, occurredAt));
    }

    @Test
    void reserveFunds_should_throw_exception_in_case_of_conflict() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(100L), usd);
        AccountOpened opened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                usd,
                initialBalance
        );
        Money firstReservedAmount = Money.of(BigDecimal.valueOf(60L), usd);
        ReservationId reservationId = ReservationId.newId();
        FundsReserved fundsReserved = new FundsReserved(
                accountId,
                Instant.now(),
                2L,
                reservationId,
                firstReservedAmount
        );
        Account account = Account.rehydrate(List.of(opened, fundsReserved));
        Money newReservedAmount = Money.of(BigDecimal.valueOf(10L), usd);
        ReserveFunds reserveFunds = new ReserveFunds(accountId, reservationId, newReservedAmount);

        // Act ... Assert
        Instant occurredAt = Instant.now();
        assertThatExceptionOfType(ReservationConflictException.class)
                .isThrownBy(() -> account.handle(reserveFunds, occurredAt));
    }

    @Test
    void reserveFunds_should_return_no_event_if_reservation_done_twice() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(100L), usd);
        AccountOpened opened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                usd,
                initialBalance
        );
        Money reservedAmount = Money.of(BigDecimal.valueOf(60L), usd);
        ReservationId reservationId = ReservationId.newId();
        FundsReserved fundsReserved = new FundsReserved(
                accountId,
                Instant.now(),
                2L,
                reservationId,
                reservedAmount
        );
        Account account = Account.rehydrate(List.of(opened, fundsReserved));
        ReserveFunds reserveFunds = new ReserveFunds(accountId, reservationId, reservedAmount);
        Instant occurredAt = Instant.now();

        // Act
        List<AccountEvent> accountEvents = account.handle(reserveFunds, occurredAt);

        // Assert
        assertThat(accountEvents).isEmpty();
    }
}
