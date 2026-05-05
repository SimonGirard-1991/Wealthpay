package org.girardsimon.wealthpay.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.girardsimon.wealthpay.account.domain.command.CancelReservation;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCanceled;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.HandleResult;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.ReservationIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.testsupport.TestAccountIdGenerator;
import org.girardsimon.wealthpay.account.testsupport.TestEventIdGenerator;
import org.girardsimon.wealthpay.account.testsupport.TestReservationIdGenerator;
import org.junit.jupiter.api.Test;

class CancelReservationTest {

  private final AccountIdGenerator accountIdGenerator = new TestAccountIdGenerator();
  private final EventIdGenerator eventIdGenerator = new TestEventIdGenerator();
  private final ReservationIdGenerator reservationIdGenerator = new TestReservationIdGenerator();

  @Test
  void cancelReservation_emits_ReservationCanceled_and_update_account_reservations() {
    // Arrange
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(100L), usd);
    AccountEventMeta meta1 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, usd, initialBalance);
    Money firstReservedAmount = Money.of(BigDecimal.valueOf(60L), usd);
    ReservationId reservationId = reservationIdGenerator.newId();
    AccountEventMeta meta2 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(
            meta2, TransactionId.of(UUID.randomUUID()), reservationId, firstReservedAmount);
    List<AccountEvent> initEvents = List.of(opened, fundsReserved);
    Account account = Account.rehydrate(initEvents);
    CancelReservation cancelReservation = new CancelReservation(accountId, reservationId);

    // Act
    List<AccountEvent> cancellationEvents =
        account.handle(cancelReservation, eventIdGenerator, Instant.now()).events();
    List<AccountEvent> allEvents =
        Stream.concat(initEvents.stream(), cancellationEvents.stream()).toList();
    Account accountAfterCancellation = Account.rehydrate(allEvents);

    // Assert
    assertAll(
        () -> assertThat(allEvents).hasSize(3),
        () -> assertThat(allEvents.getLast()).isInstanceOf(ReservationCanceled.class),
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
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(100L), usd);
    AccountEventMeta meta1 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, usd, initialBalance);
    Money firstReservedAmount = Money.of(BigDecimal.valueOf(60L), usd);
    ReservationId reservationId = reservationIdGenerator.newId();
    AccountEventMeta meta2 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(
            meta2, TransactionId.of(UUID.randomUUID()), reservationId, firstReservedAmount);
    List<AccountEvent> initEvents = List.of(opened, fundsReserved);
    Account account = Account.rehydrate(initEvents);
    CancelReservation cancelReservation =
        new CancelReservation(accountId, reservationIdGenerator.newId());
    Instant occurredAt = Instant.now();

    // Act
    HandleResult result = account.handle(cancelReservation, eventIdGenerator, occurredAt);

    // Assert
    assertThat(result.hasEffect()).isFalse();
  }

  @Test
  void cancelReservation_requires_same_id_as_account() {
    // Arrange
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(100L), usd);
    AccountEventMeta meta1 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, usd, initialBalance);
    Money firstReservedAmount = Money.of(BigDecimal.valueOf(60L), usd);
    ReservationId reservationId = reservationIdGenerator.newId();
    AccountEventMeta meta2 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(
            meta2, TransactionId.of(UUID.randomUUID()), reservationId, firstReservedAmount);
    List<AccountEvent> initEvents = List.of(opened, fundsReserved);
    Account account = Account.rehydrate(initEvents);
    CancelReservation cancelReservation =
        new CancelReservation(accountIdGenerator.newId(), reservationId);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountIdMismatchException.class)
        .isThrownBy(() -> account.handle(cancelReservation, eventIdGenerator, occurredAt));
  }

  @Test
  void cancelReservation_requires_account_to_be_opened() {
    // Arrange
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta meta1 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, usd, initialBalance);
    ReservationId reservationId = reservationIdGenerator.newId();
    Money reservedAmount = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta meta2 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(
            meta2, TransactionId.of(UUID.randomUUID()), reservationId, reservedAmount);
    AccountEventMeta meta3 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 3L);
    AccountClosed closed = new AccountClosed(meta3);
    Account closedAccount = Account.rehydrate(List.of(opened, fundsReserved, closed));
    CancelReservation cancelReservation = new CancelReservation(accountId, reservationId);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountInactiveException.class)
        .isThrownBy(() -> closedAccount.handle(cancelReservation, eventIdGenerator, occurredAt));
  }
}
