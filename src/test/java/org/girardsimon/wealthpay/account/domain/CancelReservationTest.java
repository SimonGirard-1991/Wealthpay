package org.girardsimon.wealthpay.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.girardsimon.wealthpay.account.domain.command.CancelReservation;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCancelled;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.junit.jupiter.api.Test;

class CancelReservationTest {

  @Test
  void cancelReservation_emits_ReservationCanceled_and_update_account_reservations() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(100L), usd);
    AccountOpened opened = new AccountOpened(accountId, Instant.now(), 1L, usd, initialBalance);
    Money firstReservedAmount = Money.of(BigDecimal.valueOf(60L), usd);
    ReservationId reservationId = ReservationId.newId();
    FundsReserved fundsReserved =
        new FundsReserved(accountId, Instant.now(), 2L, reservationId, firstReservedAmount);
    List<AccountEvent> initEvents = List.of(opened, fundsReserved);
    Account account = Account.rehydrate(initEvents);
    CancelReservation cancelReservation = new CancelReservation(accountId, reservationId);

    // Act
    List<AccountEvent> cancellationEvents = account.handle(cancelReservation, Instant.now());
    List<AccountEvent> allEvents =
        Stream.concat(initEvents.stream(), cancellationEvents.stream()).toList();
    Account accountAfterCancellation = Account.rehydrate(allEvents);

    // Assert
    assertAll(
        () -> assertThat(allEvents).hasSize(3),
        () -> assertThat(allEvents.getLast()).isInstanceOf(ReservationCancelled.class),
        () -> assertThat(allEvents.getLast().version()).isEqualTo(3L),
        () -> assertThat(accountAfterCancellation.getBalance()).isEqualTo(initialBalance),
        () -> assertThat(accountAfterCancellation.getAvailableBalance()).isEqualTo(initialBalance),
        () -> assertThat(accountAfterCancellation.getReservations()).isEmpty(),
        () -> assertThat(accountAfterCancellation.getStatus()).isEqualTo(AccountStatus.OPENED),
        () -> assertThat(accountAfterCancellation.getVersion()).isEqualTo(3L));
  }

  @Test
  void cancelReservation_requires_existing_reservation() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(100L), usd);
    AccountOpened opened = new AccountOpened(accountId, Instant.now(), 1L, usd, initialBalance);
    Money firstReservedAmount = Money.of(BigDecimal.valueOf(60L), usd);
    ReservationId reservationId = ReservationId.newId();
    FundsReserved fundsReserved =
        new FundsReserved(accountId, Instant.now(), 2L, reservationId, firstReservedAmount);
    List<AccountEvent> initEvents = List.of(opened, fundsReserved);
    Account account = Account.rehydrate(initEvents);
    CancelReservation cancelReservation = new CancelReservation(accountId, ReservationId.newId());
    Instant occurredAt = Instant.now();

    // Act
    List<AccountEvent> accountEvents = account.handle(cancelReservation, occurredAt);

    // Assert
    assertThat(accountEvents).isEmpty();
  }

  @Test
  void cancelReservation_requires_same_id_as_account() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(100L), usd);
    AccountOpened opened = new AccountOpened(accountId, Instant.now(), 1L, usd, initialBalance);
    Money firstReservedAmount = Money.of(BigDecimal.valueOf(60L), usd);
    ReservationId reservationId = ReservationId.newId();
    FundsReserved fundsReserved =
        new FundsReserved(accountId, Instant.now(), 2L, reservationId, firstReservedAmount);
    List<AccountEvent> initEvents = List.of(opened, fundsReserved);
    Account account = Account.rehydrate(initEvents);
    CancelReservation cancelReservation = new CancelReservation(AccountId.newId(), reservationId);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountIdMismatchException.class)
        .isThrownBy(() -> account.handle(cancelReservation, occurredAt));
  }

  @Test
  void cancelReservation_requires_account_to_be_opened() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountOpened opened = new AccountOpened(accountId, Instant.now(), 1L, usd, initialBalance);
    ReservationId reservationId = ReservationId.newId();
    Money reservedAmount = Money.of(BigDecimal.valueOf(10L), usd);
    FundsReserved fundsReserved =
        new FundsReserved(accountId, Instant.now(), 2L, reservationId, reservedAmount);
    AccountClosed closed = new AccountClosed(accountId, Instant.now(), 3L);
    Account closedAccount = Account.rehydrate(List.of(opened, fundsReserved, closed));
    CancelReservation cancelReservation = new CancelReservation(accountId, reservationId);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountInactiveException.class)
        .isThrownBy(() -> closedAccount.handle(cancelReservation, occurredAt));
  }
}
