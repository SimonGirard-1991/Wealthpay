package org.girardsimon.wealthpay.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.junit.jupiter.api.Test;

class CaptureReservationTest {

  @Test
  void captureReservation_emits_reservation_captured_event_and_update_account_reservations() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(15L), currency);
    AccountOpened accountOpened =
        new AccountOpened(accountId, Instant.now(), 1L, currency, initialBalance);
    Money reservationAmount = Money.of(BigDecimal.valueOf(5L), currency);
    ReservationId reservationId = ReservationId.newId();
    FundsReserved fundsReserved =
        new FundsReserved(accountId, Instant.now(), 2L, reservationId, reservationAmount);
    List<AccountEvent> initEvents = List.of(accountOpened, fundsReserved);
    Account account = Account.rehydrate(initEvents);
    CaptureReservation captureReservation = new CaptureReservation(accountId, reservationId);

    // Act
    List<AccountEvent> captureReservationEvents = account.handle(captureReservation, Instant.now());
    List<AccountEvent> allEvents =
        Stream.concat(initEvents.stream(), captureReservationEvents.stream()).toList();
    Account accountAfterCapture = Account.rehydrate(allEvents);

    // Assert
    Money expectedBalance = Money.of(BigDecimal.valueOf(10L), currency);
    assertAll(
        () -> assertThat(allEvents).hasSize(3),
        () -> assertThat(allEvents.getLast()).isInstanceOf(ReservationCaptured.class),
        () -> assertThat(allEvents.getLast().version()).isEqualTo(3L),
        () -> assertThat(accountAfterCapture.getBalance()).isEqualTo(expectedBalance),
        () -> assertThat(accountAfterCapture.getAvailableBalance()).isEqualTo(expectedBalance),
        () ->
            assertThat(accountAfterCapture.getReservations())
                .doesNotContainEntry(reservationId, reservationAmount),
        () -> assertThat(accountAfterCapture.getStatus()).isEqualTo(AccountStatus.OPENED),
        () -> assertThat(accountAfterCapture.getVersion()).isEqualTo(3L));
  }

  @Test
  void captureReservation_returns_no_op_if_no_reservation_found() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(15L), currency);
    AccountOpened accountOpened =
        new AccountOpened(accountId, Instant.now(), 1L, currency, initialBalance);
    ReservationId reservationId = ReservationId.newId();
    List<AccountEvent> initEvents = List.of(accountOpened);
    Account account = Account.rehydrate(initEvents);
    CaptureReservation captureReservation = new CaptureReservation(accountId, reservationId);

    // Act
    List<AccountEvent> captureReservationEvents = account.handle(captureReservation, Instant.now());

    // Assert
    assertThat(captureReservationEvents).isEmpty();
  }

  @Test
  void captureReservation_requires_same_id_as_account() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(15L), currency);
    AccountOpened accountOpened =
        new AccountOpened(accountId, Instant.now(), 1L, currency, initialBalance);
    List<AccountEvent> initEvents = List.of(accountOpened);
    Account account = Account.rehydrate(initEvents);
    ReservationId reservationId = ReservationId.newId();
    AccountId otherAccountId = AccountId.newId();
    CaptureReservation captureReservation = new CaptureReservation(otherAccountId, reservationId);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountIdMismatchException.class)
        .isThrownBy(() -> account.handle(captureReservation, occurredAt));
  }

  @Test
  void captureReservation_requires_account_to_be_open() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(15L), currency);
    AccountOpened accountOpened =
        new AccountOpened(accountId, Instant.now(), 1L, currency, initialBalance);
    FundsDebited debited =
        new FundsDebited(TransactionId.newId(), accountId, Instant.now(), 2L, initialBalance);
    AccountClosed closed = new AccountClosed(accountId, Instant.now(), 3L);
    List<AccountEvent> initEvents = List.of(accountOpened, debited, closed);
    Account account = Account.rehydrate(initEvents);
    ReservationId reservationId = ReservationId.newId();
    CaptureReservation captureReservation = new CaptureReservation(accountId, reservationId);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountInactiveException.class)
        .isThrownBy(() -> account.handle(captureReservation, occurredAt));
  }
}
