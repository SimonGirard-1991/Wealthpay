package org.girardsimon.wealthpay.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.exception.AccountCurrencyMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.exception.AmountMustBePositiveException;
import org.girardsimon.wealthpay.account.domain.exception.InsufficientFundsException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.EventId;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.HandleResult;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.testsupport.TestEventIdGenerator;
import org.junit.jupiter.api.Test;

class ReserveFundsTest {

  private final EventIdGenerator eventIdGenerator = new TestEventIdGenerator();

  @Test
  void reserveFunds_emits_FundsReserved_event_and_update_account_reservations() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(15L), currency);
    OpenAccount openAccount = new OpenAccount(currency, initialBalance);
    Money reservationAmount = Money.of(BigDecimal.valueOf(5L), currency);
    ReservationId reservationId = ReservationId.newId();
    TransactionId transactionId = TransactionId.newId();
    ReserveFunds reserveFunds = new ReserveFunds(transactionId, accountId, reservationAmount);

    // Act
    HandleResult result = Account.handle(openAccount, accountId, eventIdGenerator, Instant.now());
    List<AccountEvent> openingEvents = result.events();
    Account account = Account.rehydrate(openingEvents);
    List<AccountEvent> reserveFundsEvents =
        account.handle(reserveFunds, eventIdGenerator, reservationId, Instant.now()).events();
    List<AccountEvent> allEvents =
        Stream.concat(openingEvents.stream(), reserveFundsEvents.stream()).toList();
    Account accountAfterReservation = Account.rehydrate(allEvents);

    // Assert
    Money expectedBalance = Money.of(BigDecimal.valueOf(10L), currency);
    assertAll(
        () -> assertThat(allEvents).hasSize(2),
        () -> assertThat(allEvents.getLast()).isInstanceOf(FundsReserved.class),
        () -> assertThat(allEvents.getLast().version()).isEqualTo(2L),
        () -> assertThat(accountAfterReservation.getBalance()).isEqualTo(initialBalance),
        () -> assertThat(accountAfterReservation.getAvailableBalance()).isEqualTo(expectedBalance),
        () ->
            assertThat(accountAfterReservation.getReservations())
                .containsEntry(reservationId, reservationAmount),
        () -> assertThat(accountAfterReservation.getStatus()).isEqualTo(AccountStatus.OPENED),
        () -> assertThat(accountAfterReservation.getVersion()).isEqualTo(2L));
  }

  @Test
  void reserveFunds_requires_same_currency_as_account() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta meta = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(meta, usd, initialBalance);
    Account account = Account.rehydrate(List.of(accountOpened));
    SupportedCurrency chf = SupportedCurrency.CHF;
    Money reservedAmount = Money.of(BigDecimal.valueOf(5L), chf);
    ReservationId reservationId = ReservationId.newId();
    TransactionId transactionId = TransactionId.newId();
    ReserveFunds reserveFunds = new ReserveFunds(transactionId, accountId, reservedAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountCurrencyMismatchException.class)
        .isThrownBy(
            () -> account.handle(reserveFunds, eventIdGenerator, reservationId, occurredAt));
  }

  @Test
  void reserveFunds_requires_same_id_as_account() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta meta = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(meta, usd, initialBalance);
    Account account = Account.rehydrate(List.of(accountOpened));
    Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
    AccountId otherAccountId = AccountId.newId();
    ReservationId reservationId = ReservationId.newId();
    TransactionId transactionId = TransactionId.newId();
    ReserveFunds reserveFunds = new ReserveFunds(transactionId, otherAccountId, reservedAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountIdMismatchException.class)
        .isThrownBy(
            () -> account.handle(reserveFunds, eventIdGenerator, reservationId, occurredAt));
  }

  @Test
  void reserveFunds_requires_strictly_positive_amount() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta meta = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(meta, usd, initialBalance);
    Account account = Account.rehydrate(List.of(accountOpened));
    Money reservedAmount = Money.of(BigDecimal.valueOf(-5L), usd);
    ReservationId reservationId = ReservationId.newId();
    TransactionId transactionId = TransactionId.newId();
    ReserveFunds reserveFunds = new ReserveFunds(transactionId, accountId, reservedAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AmountMustBePositiveException.class)
        .isThrownBy(
            () -> account.handle(reserveFunds, eventIdGenerator, reservationId, occurredAt));
  }

  @Test
  void reserveFunds_requires_account_to_be_opened() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta meta1 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, usd, initialBalance);
    AccountEventMeta meta2 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsDebited debited = new FundsDebited(meta2, TransactionId.newId(), initialBalance);
    AccountClosed closed =
        new AccountClosed(AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 3L));
    Account closedAccount = Account.rehydrate(List.of(opened, debited, closed));
    Money reservedAmount = Money.of(BigDecimal.valueOf(10L), usd);
    ReservationId reservationId = ReservationId.newId();
    TransactionId transactionId = TransactionId.newId();
    ReserveFunds reserveFunds = new ReserveFunds(transactionId, accountId, reservedAmount);

    // Act + Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountInactiveException.class)
        .isThrownBy(
            () -> closedAccount.handle(reserveFunds, eventIdGenerator, reservationId, occurredAt));
  }

  @Test
  void reserveFunds_requires_reserved_amount_to_be_less_than_account_available_balance() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(100L), usd);
    AccountEventMeta meta1 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, usd, initialBalance);
    Money firstReservedAmount = Money.of(BigDecimal.valueOf(60L), usd);
    AccountEventMeta meta2 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(meta2, TransactionId.newId(), ReservationId.newId(), firstReservedAmount);
    Account account = Account.rehydrate(List.of(opened, fundsReserved));
    Money reservedAmount =
        Money.of(BigDecimal.valueOf(50L), usd); // 50 > 100 - 60 = 40 available balance
    ReserveFunds reserveFunds = new ReserveFunds(TransactionId.newId(), accountId, reservedAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    ReservationId reservationId = ReservationId.newId();
    assertThatExceptionOfType(InsufficientFundsException.class)
        .isThrownBy(
            () -> account.handle(reserveFunds, eventIdGenerator, reservationId, occurredAt));
  }
}
