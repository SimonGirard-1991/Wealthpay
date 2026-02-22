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
import java.util.List;
import java.util.Optional;
import org.girardsimon.wealthpay.account.application.response.ReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReservationResult;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsResponse;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
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
import org.girardsimon.wealthpay.account.domain.exception.AccountHistoryNotFound;
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCanceledException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCapturedException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationNotFoundException;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
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
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceTest {

  public static final Instant INSTANT_FOR_TESTS = Instant.parse("2025-11-16T15:00:00Z");
  AccountEventStore accountEventStore = mock(AccountEventStore.class);
  AccountBalanceReader accountBalanceReader = mock(AccountBalanceReader.class);
  AccountEventPublisher accountEventPublisher = mock(AccountEventPublisher.class);
  ProcessedTransactionStore processedTransactionStore = mock(ProcessedTransactionStore.class);
  ProcessedReservationStore processedReservationStore = mock(ProcessedReservationStore.class);

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
          accountBalanceReader,
          accountEventPublisher,
          processedTransactionStore,
          processedReservationStore,
          clock,
          accountIdGenerator,
          eventIdGenerator,
          reservationIdGenerator);

  @Test
  void openAccount_saves_event_AccountOpened_when_account_does_not_exist() {
    // Arrange
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = new Money(BigDecimal.valueOf(10L), currency);
    OpenAccount openAccount = new OpenAccount(currency, initialBalance);
    when(accountEventStore.loadEvents(accountId)).thenReturn(List.of());

    // Act
    accountApplicationService.openAccount(openAccount);

    // Assert
    AccountEventMeta accountEventMeta =
        AccountEventMeta.of(eventId, accountId, Instant.parse("2025-11-16T15:00:00Z"), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta, currency, initialBalance);
    InOrder inOrder = inOrder(accountEventStore, accountEventPublisher);
    inOrder.verify(accountEventStore).appendEvents(accountId, 0L, List.of(accountOpened));
    inOrder.verify(accountEventPublisher).publish(List.of(accountOpened));
  }

  @Test
  void getAccountBalance_should_return_account_balance_view_for_given_id() {
    // Arrange
    AccountId uuid = AccountId.newId();
    AccountBalanceView mock = mock(AccountBalanceView.class);
    when(accountBalanceReader.getAccountBalance(uuid)).thenReturn(mock);

    // Act
    AccountBalanceView accountBalanceView = accountApplicationService.getAccountBalance(uuid);

    // Assert
    assertThat(accountBalanceView).isEqualTo(mock);
    verifyNoInteractions(accountEventStore);
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
    when(processedReservationStore.lookup(accountId, otherReservationId))
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
    when(processedReservationStore.lookup(accountId, otherReservationId))
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
    when(processedReservationStore.lookup(accountId, otherReservationId))
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
    assertThatExceptionOfType(AccountHistoryNotFound.class)
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
    when(processedReservationStore.lookup(accountId, otherReservationId))
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
    when(processedReservationStore.lookup(accountId, otherReservationId))
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
    when(processedReservationStore.lookup(accountId, otherReservationId))
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
    assertThatExceptionOfType(AccountHistoryNotFound.class)
        .isThrownBy(() -> accountApplicationService.cancelReservation(cancelReservation));
  }

  @Test
  void creditAccount_should_not_persist_event_when_transaction_status_is_no_effect() {
    // Arrange
    TransactionId transactionId = TransactionId.newId();
    Money money = Money.of(new BigDecimal("100.00"), SupportedCurrency.EUR);
    CreditAccount creditAccount = new CreditAccount(transactionId, accountId, money);
    when(processedTransactionStore.register(accountId, transactionId, INSTANT_FOR_TESTS))
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
    when(processedTransactionStore.register(accountId, transactionId, INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.COMMITTED);
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(new BigDecimal("10.00"), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    List<AccountEvent> accountEvents = List.of(accountOpened);
    when(accountEventStore.loadEvents(accountId)).thenReturn(accountEvents);
    Money money = Money.of(new BigDecimal("50.00"), SupportedCurrency.USD);
    CreditAccount creditAccount = new CreditAccount(transactionId, accountId, money);

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
  void debitAccount_should_not_persist_event_when_transaction_status_is_no_effect() {
    // Arrange
    TransactionId transactionId = TransactionId.newId();
    Money money = Money.of(new BigDecimal("100.00"), SupportedCurrency.EUR);
    DebitAccount debitAccount = new DebitAccount(transactionId, accountId, money);
    when(processedTransactionStore.register(accountId, transactionId, INSTANT_FOR_TESTS))
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
    when(processedTransactionStore.register(accountId, transactionId, INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.COMMITTED);
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(new BigDecimal("10.00"), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    List<AccountEvent> accountEvents = List.of(accountOpened);
    when(accountEventStore.loadEvents(accountId)).thenReturn(accountEvents);
    Money money = Money.of(new BigDecimal("5.00"), SupportedCurrency.USD);
    DebitAccount debitAccount = new DebitAccount(transactionId, accountId, money);

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
    when(processedTransactionStore.register(accountId, transactionId, INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.NO_EFFECT);
    when(processedReservationStore.lookup(accountId, transactionId)).thenReturn(reservationId);

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
    when(processedTransactionStore.register(accountId, transactionId, INSTANT_FOR_TESTS))
        .thenReturn(TransactionStatus.COMMITTED);
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(new BigDecimal("10.00"), usd);
    AccountEventMeta accountEventMeta1 =
        AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(accountEventMeta1, usd, initialBalance);
    when(accountEventStore.loadEvents(accountId)).thenReturn(List.of(accountOpened));
    Money money = Money.of(new BigDecimal("5.00"), SupportedCurrency.USD);
    ReserveFunds reserveFunds = new ReserveFunds(transactionId, accountId, money);

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
}
