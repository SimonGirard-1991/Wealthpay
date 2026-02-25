package org.girardsimon.wealthpay.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.girardsimon.wealthpay.account.application.response.ReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReservationResult;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsResponse;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.domain.command.CancelReservation;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCanceled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.exception.AccountHistoryNotFoundException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCanceledException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCapturedException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationNotFoundException;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.AccountSnapshot;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.EventId;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.ReservationIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.ReservationPhase;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceTest {

  public static final Instant INSTANT_FOR_TESTS = Instant.parse("2025-11-16T15:00:00Z");
  public static final int SNAPSHOT_THRESHOLD = 100;
  AccountEventStore accountEventStore = mock(AccountEventStore.class);
  AccountEventPublisher accountEventPublisher = mock(AccountEventPublisher.class);
  ProcessedTransactionStore processedTransactionStore = mock(ProcessedTransactionStore.class);
  ProcessedReservationStore processedReservationStore = mock(ProcessedReservationStore.class);
  AccountSnapshotStore accountSnapshotStore = mock(AccountSnapshotStore.class);

  Clock clock = Clock.fixed(INSTANT_FOR_TESTS, ZoneOffset.UTC);

  AccountId accountId = AccountId.newId();
  EventId eventId = EventId.newId();
  ReservationId reservationId = ReservationId.newId();

  AccountIdGenerator accountIdGenerator = () -> accountId;
  EventIdGenerator eventIdGenerator = () -> eventId;
  ReservationIdGenerator reservationIdGenerator = () -> reservationId;

  AccountApplicationService accountApplicationService =
      new AccountApplicationService(
          accountEventStore,
          accountEventPublisher,
          processedTransactionStore,
          processedReservationStore,
          accountSnapshotStore,
          clock,
          accountIdGenerator,
          eventIdGenerator,
          reservationIdGenerator,
          SNAPSHOT_THRESHOLD);

  @Test
  void constructor_should_throw_when_snapshot_threshold_is_not_positive() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                new AccountApplicationService(
                    accountEventStore,
                    accountEventPublisher,
                    processedTransactionStore,
                    processedReservationStore,
                    accountSnapshotStore,
                    clock,
                    accountIdGenerator,
                    eventIdGenerator,
                    reservationIdGenerator,
                    0))
        .withMessageContaining("account-event.snapshot.threshold")
        .withMessageContaining("> 0");
  }

  @Test
  void openAccount_saves_event_AccountOpened_when_account_does_not_exist() {
    // Arrange
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = new Money(BigDecimal.valueOf(10L), currency);
    OpenAccount openAccount = new OpenAccount(currency, initialBalance);

    // Act
    accountApplicationService.openAccount(openAccount);

    // Assert
    AccountEventMeta accountEventMeta =
        AccountEventMeta.of(eventId, accountId, Instant.parse("2025-11-16T15:00:00Z"), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta, currency, initialBalance);
    InOrder inOrder = inOrder(accountEventStore, accountEventPublisher);
    inOrder.verify(accountEventStore).appendEvents(accountId, 0L, List.of(accountOpened));
    inOrder.verify(accountEventPublisher).publish(List.of(accountOpened));
    verify(accountSnapshotStore, never()).saveSnapshot(any());
  }

  @Test
  void captureReservation_should_save_reservation_captured_event_when_reservation_exists() {
    // Arrange
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
    TransactionId transactionId = TransactionId.newId();
    AccountEventMeta accountEventMeta2 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(accountEventMeta2, transactionId, reservationId, reservedAmount);
    List<AccountEvent> accountEvents = List.of(accountOpened, fundsReserved);
    when(accountEventStore.loadEvents(accountId)).thenReturn(accountEvents);
    CaptureReservation captureReservation = new CaptureReservation(accountId, reservationId);
    when(accountSnapshotStore.load(accountId)).thenReturn(Optional.empty());

    // Act
    ReservationResponse captureReservationResponse =
        accountApplicationService.captureReservation(captureReservation);

    // Assert
    AccountEventMeta accountEventMeta =
        AccountEventMeta.of(eventId, accountId, INSTANT_FOR_TESTS, 3L);
    ReservationCaptured reservationCaptured =
        new ReservationCaptured(accountEventMeta, reservationId, reservedAmount);
    InOrder inOrder = inOrder(accountEventStore, accountEventPublisher, processedReservationStore);
    inOrder.verify(accountEventStore).appendEvents(accountId, 2L, List.of(reservationCaptured));
    inOrder.verify(accountEventPublisher).publish(List.of(reservationCaptured));
    inOrder
        .verify(processedReservationStore)
        .updatePhase(accountId, reservationId, ReservationPhase.CAPTURED, INSTANT_FOR_TESTS);
    assertAll(
        () -> assertThat(captureReservationResponse.accountId()).isEqualTo(accountId),
        () -> assertThat(captureReservationResponse.reservationId()).isEqualTo(reservationId),
        () ->
            assertThat(captureReservationResponse.reservationResult())
                .isEqualTo(ReservationResult.CAPTURED),
        () -> assertThat(captureReservationResponse.money()).contains(reservedAmount));
  }

  @Test
  void captureReservation_should_have_idempotent_behavior_when_reservation_is_already_captured() {
    // Arrange
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
    TransactionId transactionId = TransactionId.newId();
    AccountEventMeta accountEventMeta2 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(accountEventMeta2, transactionId, reservationId, reservedAmount);
    List<AccountEvent> accountEvents = List.of(accountOpened, fundsReserved);
    when(accountEventStore.loadEvents(accountId)).thenReturn(accountEvents);
    ReservationId otherReservationId = ReservationId.newId();
    CaptureReservation captureReservation = new CaptureReservation(accountId, otherReservationId);
    when(processedReservationStore.lookupPhase(accountId, otherReservationId))
        .thenReturn(Optional.of(ReservationPhase.CAPTURED));

    // Act
    ReservationResponse captureReservationResponse =
        accountApplicationService.captureReservation(captureReservation);

    // Assert
    verify(accountEventStore, never()).appendEvents(any(), anyLong(), any());
    verify(accountEventPublisher, never()).publish(any());
    assertAll(
        () -> assertThat(captureReservationResponse.accountId()).isEqualTo(accountId),
        () -> assertThat(captureReservationResponse.reservationId()).isEqualTo(otherReservationId),
        () ->
            assertThat(captureReservationResponse.reservationResult())
                .isEqualTo(ReservationResult.NO_EFFECT),
        () -> assertThat(captureReservationResponse.money()).isEmpty());
  }

  @Test
  void captureReservation_should_throw_reservation_not_found_when_reservation_does_not_exist() {
    // Arrange
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
    TransactionId transactionId = TransactionId.newId();
    AccountEventMeta accountEventMeta2 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(accountEventMeta2, transactionId, reservationId, reservedAmount);
    List<AccountEvent> accountEvents = List.of(accountOpened, fundsReserved);
    when(accountEventStore.loadEvents(accountId)).thenReturn(accountEvents);
    ReservationId otherReservationId = ReservationId.newId();
    CaptureReservation captureReservation = new CaptureReservation(accountId, otherReservationId);
    when(processedReservationStore.lookupPhase(accountId, otherReservationId))
        .thenReturn(Optional.empty());

    // Act ... Assert
    assertThatExceptionOfType(ReservationNotFoundException.class)
        .isThrownBy(() -> accountApplicationService.captureReservation(captureReservation));
  }

  @Test
  void
      captureReservation_should_throw_reservation_already_canceled_when_reservation_is_already_canceled() {
    // Arrange
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
    TransactionId transactionId = TransactionId.newId();
    AccountEventMeta accountEventMeta2 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(accountEventMeta2, transactionId, reservationId, reservedAmount);
    List<AccountEvent> accountEvents = List.of(accountOpened, fundsReserved);
    when(accountEventStore.loadEvents(accountId)).thenReturn(accountEvents);
    ReservationId otherReservationId = ReservationId.newId();
    CaptureReservation captureReservation = new CaptureReservation(accountId, otherReservationId);
    when(processedReservationStore.lookupPhase(accountId, otherReservationId))
        .thenReturn(Optional.of(ReservationPhase.CANCELED));

    // Act ... Assert
    assertThatExceptionOfType(ReservationAlreadyCanceledException.class)
        .isThrownBy(() -> accountApplicationService.captureReservation(captureReservation));
  }

  @Test
  void captureReservation_should_throw_account_history_not_found_when_no_corresponding_account() {
    // Arrange
    CaptureReservation captureReservation =
        new CaptureReservation(accountId, ReservationId.newId());
    when(accountEventStore.loadEvents(accountId)).thenReturn(List.of());

    // Act ... Assert
    assertThatExceptionOfType(AccountHistoryNotFoundException.class)
        .isThrownBy(() -> accountApplicationService.captureReservation(captureReservation));
  }

  @Test
  void cancelReservation_should_save_reservation_canceled_event_when_reservation_exists() {
    // Arrange
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
    TransactionId transactionId = TransactionId.newId();
    AccountEventMeta accountEventMeta2 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(accountEventMeta2, transactionId, reservationId, reservedAmount);
    when(accountEventStore.loadEvents(accountId)).thenReturn(List.of(accountOpened, fundsReserved));
    CancelReservation cancelReservation = new CancelReservation(accountId, reservationId);

    // Act
    ReservationResponse reservationResponse =
        accountApplicationService.cancelReservation(cancelReservation);

    // Assert
    AccountEventMeta accountEventMeta =
        AccountEventMeta.of(eventId, accountId, INSTANT_FOR_TESTS, 3L);
    ReservationCanceled reservationCanceled =
        new ReservationCanceled(accountEventMeta, reservationId, reservedAmount);
    InOrder inOrder = inOrder(accountEventStore, accountEventPublisher, processedReservationStore);
    inOrder.verify(accountEventStore).appendEvents(accountId, 2L, List.of(reservationCanceled));
    inOrder.verify(accountEventPublisher).publish(List.of(reservationCanceled));
    inOrder
        .verify(processedReservationStore)
        .updatePhase(accountId, reservationId, ReservationPhase.CANCELED, INSTANT_FOR_TESTS);
    assertAll(
        () -> assertThat(reservationResponse.accountId()).isEqualTo(accountId),
        () -> assertThat(reservationResponse.reservationId()).isEqualTo(reservationId),
        () ->
            assertThat(reservationResponse.reservationResult())
                .isEqualTo(ReservationResult.CANCELED),
        () -> assertThat(reservationResponse.money()).contains(reservedAmount));
  }

  @Test
  void cancelReservation_should_have_idempotent_behavior_when_reservation_is_already_canceled() {
    // Arrange
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
    TransactionId transactionId = TransactionId.newId();
    AccountEventMeta accountEventMeta2 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(accountEventMeta2, transactionId, reservationId, reservedAmount);
    when(accountEventStore.loadEvents(accountId)).thenReturn(List.of(accountOpened, fundsReserved));
    ReservationId otherReservationId = ReservationId.newId();
    CancelReservation cancelReservation = new CancelReservation(accountId, otherReservationId);
    when(processedReservationStore.lookupPhase(accountId, otherReservationId))
        .thenReturn(Optional.of(ReservationPhase.CANCELED));

    // Act
    ReservationResponse reservationResponse =
        accountApplicationService.cancelReservation(cancelReservation);

    // Assert
    verify(accountEventStore, never()).appendEvents(any(), anyLong(), any());
    verify(accountEventPublisher, never()).publish(any());
    verify(processedReservationStore, never()).updatePhase(any(), any(), any(), any());
    assertAll(
        () -> assertThat(reservationResponse.accountId()).isEqualTo(accountId),
        () -> assertThat(reservationResponse.reservationId()).isEqualTo(otherReservationId),
        () ->
            assertThat(reservationResponse.reservationResult())
                .isEqualTo(ReservationResult.NO_EFFECT),
        () -> assertThat(reservationResponse.money()).isEmpty());
  }

  @Test
  void cancelReservation_should_throw_reservation_not_found_when_reservation_does_not_exist() {
    // Arrange
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
    TransactionId transactionId = TransactionId.newId();
    AccountEventMeta accountEventMeta2 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(accountEventMeta2, transactionId, reservationId, reservedAmount);
    when(accountEventStore.loadEvents(accountId)).thenReturn(List.of(accountOpened, fundsReserved));
    ReservationId otherReservationId = ReservationId.newId();
    CancelReservation cancelReservation = new CancelReservation(accountId, otherReservationId);
    when(processedReservationStore.lookupPhase(accountId, otherReservationId))
        .thenReturn(Optional.empty());

    // Act ... Assert
    assertThatExceptionOfType(ReservationNotFoundException.class)
        .isThrownBy(() -> accountApplicationService.cancelReservation(cancelReservation));
  }

  @Test
  void
      cancelReservation_should_throw_reservation_already_captured_when_reservation_is_already_captured() {
    // Arrange
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
    TransactionId transactionId = TransactionId.newId();
    AccountEventMeta accountEventMeta2 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved =
        new FundsReserved(accountEventMeta2, transactionId, reservationId, reservedAmount);
    when(accountEventStore.loadEvents(accountId)).thenReturn(List.of(accountOpened, fundsReserved));
    ReservationId otherReservationId = ReservationId.newId();
    CancelReservation cancelReservation = new CancelReservation(accountId, otherReservationId);
    when(processedReservationStore.lookupPhase(accountId, otherReservationId))
        .thenReturn(Optional.of(ReservationPhase.CAPTURED));

    // Act ... Assert
    assertThatExceptionOfType(ReservationAlreadyCapturedException.class)
        .isThrownBy(() -> accountApplicationService.cancelReservation(cancelReservation));
  }

  @Test
  void cancelReservation_should_throw_account_history_not_found_when_no_corresponding_account() {
    // Arrange
    CancelReservation cancelReservation = new CancelReservation(accountId, ReservationId.newId());
    when(accountEventStore.loadEvents(accountId)).thenReturn(List.of());

    // Act ... Assert
    assertThatExceptionOfType(AccountHistoryNotFoundException.class)
        .isThrownBy(() -> accountApplicationService.cancelReservation(cancelReservation));
  }

  @Test
  void creditAccount_should_not_persist_event_when_transaction_status_is_no_effect() {
    // Arrange
    TransactionId transactionId = TransactionId.newId();
    Money money = Money.of(new BigDecimal("100.00"), SupportedCurrency.EUR);
    CreditAccount creditAccount = new CreditAccount(transactionId, accountId, money);
    when(processedTransactionStore.register(
            accountId, transactionId, creditAccount.fingerprint(), INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.NO_EFFECT);

    // Act
    TransactionStatus transactionStatus = accountApplicationService.creditAccount(creditAccount);

    // Assert
    assertThat(transactionStatus).isEqualTo(TransactionStatus.NO_EFFECT);
    verifyNoInteractions(accountEventStore);
    verifyNoInteractions(accountEventPublisher);
  }

  @Test
  void creditAccount_should_save_funds_credited_event_when_transaction_status_is_committed() {
    // Arrange
    TransactionId transactionId = TransactionId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(new BigDecimal("10.00"), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    List<AccountEvent> accountEvents = List.of(accountOpened);
    when(accountEventStore.loadEvents(accountId)).thenReturn(accountEvents);
    Money money = Money.of(new BigDecimal("50.00"), SupportedCurrency.USD);
    CreditAccount creditAccount = new CreditAccount(transactionId, accountId, money);
    when(processedTransactionStore.register(
            accountId, transactionId, creditAccount.fingerprint(), INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.COMMITTED);

    // Act
    TransactionStatus transactionStatus = accountApplicationService.creditAccount(creditAccount);

    // Assert
    AccountEventMeta accountEventMeta =
        AccountEventMeta.of(eventId, accountId, INSTANT_FOR_TESTS, 2L);
    FundsCredited fundsCredited = new FundsCredited(accountEventMeta, transactionId, money);
    InOrder inOrder = inOrder(accountEventStore, accountEventPublisher);
    inOrder.verify(accountEventStore).appendEvents(accountId, 1L, List.of(fundsCredited));
    inOrder.verify(accountEventPublisher).publish(List.of(fundsCredited));
    assertThat(transactionStatus).isEqualTo(TransactionStatus.COMMITTED);
  }

  @Test
  void
      creditAccount_should_load_from_snapshot_without_saving_new_snapshot_when_threshold_not_crossed() {
    // Arrange
    TransactionId transactionId = TransactionId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(new BigDecimal("250.00"), usd);
    AccountSnapshot accountSnapshot =
        new AccountSnapshot(accountId, usd, initialBalance, AccountStatus.OPENED, Map.of(), 100L);
    when(accountSnapshotStore.load(accountId)).thenReturn(Optional.of(accountSnapshot));
    when(accountEventStore.loadEventsAfterVersion(accountId, 100L)).thenReturn(List.of());
    Money money = Money.of(new BigDecimal("5.00"), SupportedCurrency.USD);
    CreditAccount creditAccount = new CreditAccount(transactionId, accountId, money);
    when(processedTransactionStore.register(
            accountId, transactionId, creditAccount.fingerprint(), INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.COMMITTED);

    // Act
    TransactionStatus transactionStatus = accountApplicationService.creditAccount(creditAccount);

    // Assert
    AccountEventMeta accountEventMeta =
        AccountEventMeta.of(eventId, accountId, INSTANT_FOR_TESTS, 101L);
    FundsCredited fundsCredited = new FundsCredited(accountEventMeta, transactionId, money);
    InOrder inOrder = inOrder(accountSnapshotStore, accountEventStore, accountEventPublisher);
    inOrder.verify(accountSnapshotStore).load(accountId);
    inOrder.verify(accountEventStore).loadEventsAfterVersion(accountId, 100L);
    inOrder.verify(accountEventStore).appendEvents(accountId, 100L, List.of(fundsCredited));
    inOrder.verify(accountEventPublisher).publish(List.of(fundsCredited));
    verify(accountSnapshotStore, never()).saveSnapshot(any());
    assertThat(transactionStatus).isEqualTo(TransactionStatus.COMMITTED);
    verify(accountEventStore, never()).loadEvents(accountId);
  }

  @Test
  void creditAccount_should_save_snapshot_when_threshold_is_crossed_without_initial_snapshot() {
    // Arrange
    TransactionId transactionId = TransactionId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    List<AccountEvent> historyUntilVersion99 =
        buildHistory(accountId, usd, SNAPSHOT_THRESHOLD - 1L);
    when(accountSnapshotStore.load(accountId)).thenReturn(Optional.empty());
    when(accountEventStore.loadEvents(accountId)).thenReturn(historyUntilVersion99);
    Money money = Money.of(new BigDecimal("5.00"), SupportedCurrency.USD);
    CreditAccount creditAccount = new CreditAccount(transactionId, accountId, money);
    when(processedTransactionStore.register(
            accountId, transactionId, creditAccount.fingerprint(), INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.COMMITTED);

    // Act
    TransactionStatus transactionStatus = accountApplicationService.creditAccount(creditAccount);

    // Assert
    AccountEventMeta accountEventMeta =
        AccountEventMeta.of(eventId, accountId, INSTANT_FOR_TESTS, 100L);
    FundsCredited fundsCredited = new FundsCredited(accountEventMeta, transactionId, money);
    ArgumentCaptor<AccountSnapshot> accountCaptor = ArgumentCaptor.forClass(AccountSnapshot.class);
    InOrder inOrder = inOrder(accountSnapshotStore, accountEventStore, accountEventPublisher);
    inOrder.verify(accountSnapshotStore).load(accountId);
    inOrder.verify(accountEventStore).loadEvents(accountId);
    inOrder.verify(accountEventStore).appendEvents(accountId, 99L, List.of(fundsCredited));
    inOrder.verify(accountEventPublisher).publish(List.of(fundsCredited));
    inOrder.verify(accountSnapshotStore).saveSnapshot(accountCaptor.capture());
    assertAll(
        () -> assertThat(transactionStatus).isEqualTo(TransactionStatus.COMMITTED),
        () -> assertThat(accountCaptor.getValue().version()).isEqualTo(100L),
        () ->
            assertThat(accountCaptor.getValue().balance())
                .isEqualTo(Money.of(new BigDecimal("113.00"), usd)));
    verify(accountEventStore, never()).loadEventsAfterVersion(any(), anyLong());
  }

  @Test
  void debitAccount_should_not_persist_event_when_transaction_status_is_no_effect() {
    // Arrange
    TransactionId transactionId = TransactionId.newId();
    Money money = Money.of(new BigDecimal("100.00"), SupportedCurrency.EUR);
    DebitAccount debitAccount = new DebitAccount(transactionId, accountId, money);
    when(processedTransactionStore.register(
            accountId, transactionId, debitAccount.fingerprint(), INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.NO_EFFECT);

    // Act
    TransactionStatus transactionStatus = accountApplicationService.debitAccount(debitAccount);

    // Assert
    assertThat(transactionStatus).isEqualTo(TransactionStatus.NO_EFFECT);
    verifyNoInteractions(accountEventStore);
    verifyNoInteractions(accountEventPublisher);
  }

  @Test
  void debitAccount_should_save_funds_debited_event_when_transaction_status_is_committed() {
    // Arrange
    TransactionId transactionId = TransactionId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(new BigDecimal("10.00"), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    List<AccountEvent> accountEvents = List.of(accountOpened);
    when(accountEventStore.loadEvents(accountId)).thenReturn(accountEvents);
    Money money = Money.of(new BigDecimal("5.00"), SupportedCurrency.USD);
    DebitAccount debitAccount = new DebitAccount(transactionId, accountId, money);
    when(processedTransactionStore.register(
            accountId, transactionId, debitAccount.fingerprint(), INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.COMMITTED);

    // Act
    TransactionStatus transactionStatus = accountApplicationService.debitAccount(debitAccount);

    // Assert
    AccountEventMeta accountEventMeta =
        AccountEventMeta.of(eventId, accountId, INSTANT_FOR_TESTS, 2L);
    FundsDebited fundsDebited = new FundsDebited(accountEventMeta, transactionId, money);
    InOrder inOrder = inOrder(accountEventStore, accountEventPublisher);
    inOrder.verify(accountEventStore).appendEvents(accountId, 1L, List.of(fundsDebited));
    inOrder.verify(accountEventPublisher).publish(List.of(fundsDebited));
    assertThat(transactionStatus).isEqualTo(TransactionStatus.COMMITTED);
  }

  @Test
  void reserveFunds_should_not_persist_event_when_transaction_status_is_no_effect() {
    // Arrange
    TransactionId transactionId = TransactionId.newId();
    Money money = Money.of(new BigDecimal("100.00"), SupportedCurrency.USD);
    ReserveFunds reserveFunds = new ReserveFunds(transactionId, accountId, money);
    when(processedTransactionStore.register(
            accountId, transactionId, reserveFunds.fingerprint(), INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.NO_EFFECT);
    when(processedReservationStore.lookupReservation(accountId, transactionId))
        .thenReturn(reservationId);

    // Act
    ReserveFundsResponse reserveFundsResponse =
        accountApplicationService.reserveFunds(reserveFunds);

    // Assert
    verifyNoInteractions(accountEventStore);
    verifyNoInteractions(accountEventPublisher);
    verify(processedReservationStore, never()).register(any(), any(), any(), any(), any());
    assertAll(
        () -> assertThat(reserveFundsResponse.reservationId()).isEqualTo(reservationId),
        () ->
            assertThat(reserveFundsResponse.reservationResult())
                .isEqualTo(ReservationResult.NO_EFFECT));
  }

  @Test
  void reserveFunds_should_save_funds_reserved_event_when_transaction_status_is_committed() {
    // Arrange
    TransactionId transactionId = TransactionId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(new BigDecimal("10.00"), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    when(accountEventStore.loadEvents(accountId)).thenReturn(List.of(accountOpened));
    Money money = Money.of(new BigDecimal("5.00"), SupportedCurrency.USD);
    ReserveFunds reserveFunds = new ReserveFunds(transactionId, accountId, money);
    when(processedTransactionStore.register(
            accountId, transactionId, reserveFunds.fingerprint(), INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.COMMITTED);

    // Act
    ReserveFundsResponse reserveFundsResponse =
        accountApplicationService.reserveFunds(reserveFunds);

    // Assert
    AccountEventMeta accountEventMeta =
        AccountEventMeta.of(eventId, accountId, INSTANT_FOR_TESTS, 2L);
    FundsReserved fundsReserved =
        new FundsReserved(accountEventMeta, transactionId, reservationId, money);
    InOrder inOrder = inOrder(accountEventStore, accountEventPublisher, processedReservationStore);
    inOrder.verify(accountEventStore).appendEvents(accountId, 1L, List.of(fundsReserved));
    inOrder.verify(accountEventPublisher).publish(List.of(fundsReserved));
    inOrder
        .verify(processedReservationStore)
        .register(
            accountId, transactionId, reservationId, ReservationPhase.RESERVED, INSTANT_FOR_TESTS);
    assertAll(
        () -> assertThat(reserveFundsResponse.reservationId()).isEqualTo(reservationId),
        () ->
            assertThat(reserveFundsResponse.reservationResult())
                .isEqualTo(ReservationResult.RESERVED));
  }

  private List<AccountEvent> buildHistory(
      AccountId accountId, SupportedCurrency currency, Long numberOfEvents) {
    List<AccountEvent> events = new ArrayList<>();

    Money initialBalance = Money.of(new BigDecimal("10.00"), currency);
    AccountEventMeta accountOpenedMeta =
        AccountEventMeta.of(EventId.newId(), accountId, INSTANT_FOR_TESTS.minusSeconds(99L), 1L);
    events.add(new AccountOpened(accountOpenedMeta, currency, initialBalance));

    Money historicalCreditAmount = Money.of(BigDecimal.ONE, currency);
    for (long version = 2L; version <= numberOfEvents; version++) {
      AccountEventMeta fundsCreditedMeta =
          AccountEventMeta.of(
              EventId.newId(), accountId, INSTANT_FOR_TESTS.minusSeconds(100L - version), version);
      events.add(
          new FundsCredited(fundsCreditedMeta, TransactionId.newId(), historicalCreditAmount));
    }
    return events;
  }
}
