package org.girardsimon.wealthpay.account.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.girardsimon.wealthpay.account.application.response.CancelReservationResponse;
import org.girardsimon.wealthpay.account.application.response.CancelReservationStatus;
import org.girardsimon.wealthpay.account.application.response.CaptureReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReservationCaptureStatus;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsResponse;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsStatus;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.command.AccountTransaction;
import org.girardsimon.wealthpay.account.domain.command.CancelReservation;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCanceledException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCapturedException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationNotFoundException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.HandleResult;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.ReservationIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.ReservationOutcome;
import org.girardsimon.wealthpay.account.domain.model.ReservationPhase;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountApplicationService {

  private final AccountEventStore accountEventStore;
  private final AccountBalanceReader accountBalanceReader;
  private final AccountEventPublisher accountEventPublisher;
  private final ProcessedTransactionStore processedTransactionStore;
  private final ProcessedReservationStore processedReservationStore;
  private final Clock clock;
  private final AccountIdGenerator accountIdGenerator;
  private final EventIdGenerator eventIdGenerator;
  private final ReservationIdGenerator reservationIdGenerator;

  public AccountApplicationService(
      AccountEventStore accountEventStore,
      AccountBalanceReader accountBalanceReader,
      AccountEventPublisher accountEventPublisher,
      ProcessedTransactionStore processedTransactionStore,
      ProcessedReservationStore processedReservationStore,
      Clock clock,
      AccountIdGenerator accountIdGenerator,
      EventIdGenerator eventIdGenerator,
      ReservationIdGenerator reservationIdGenerator) {
    this.accountEventStore = accountEventStore;
    this.accountBalanceReader = accountBalanceReader;
    this.accountEventPublisher = accountEventPublisher;
    this.processedTransactionStore = processedTransactionStore;
    this.processedReservationStore = processedReservationStore;
    this.clock = clock;
    this.accountIdGenerator = accountIdGenerator;
    this.eventIdGenerator = eventIdGenerator;
    this.reservationIdGenerator = reservationIdGenerator;
  }

  private static long versionBeforeEvents(Account account, List<AccountEvent> events) {
    return account.getVersion() - events.size();
  }

  private Account loadAccount(AccountId accountId) {
    List<AccountEvent> history = accountEventStore.loadEvents(accountId);
    return Account.rehydrate(history);
  }

  private void saveEvents(
      long expectedVersion, List<AccountEvent> accountEvents, AccountId accountId) {
    accountEventStore.appendEvents(accountId, expectedVersion, accountEvents);
    accountEventPublisher.publish(accountEvents);
  }

  @Transactional
  public AccountId openAccount(OpenAccount openAccount) {
    AccountId accountId = accountIdGenerator.newId();
    HandleResult result =
        Account.handle(openAccount, accountId, eventIdGenerator, Instant.now(clock));
    saveEvents(0L, result.events(), accountId);
    return accountId;
  }

  @Transactional(readOnly = true)
  public AccountBalanceView getAccountBalance(AccountId accountId) {
    return accountBalanceReader.getAccountBalance(accountId);
  }

  private CaptureReservationResponse handleCaptureNoEffect(
      AccountId accountId, ReservationId reservationId) {
    Optional<ReservationPhase> lookup = processedReservationStore.lookup(accountId, reservationId);

    if (lookup.isEmpty()) {
      throw new ReservationNotFoundException(reservationId);
    } else {
      return switch (lookup.get()) {
        case CANCELED -> throw new ReservationAlreadyCanceledException(reservationId);
        case CAPTURED ->
            new CaptureReservationResponse(
                accountId, reservationId, ReservationCaptureStatus.NO_EFFECT, Optional.empty());
        case RESERVED ->
            throw new IllegalStateException(
                "Should not happen: when reservation is reserved, capture should not return no effect.");
      };
    }
  }

  @Transactional
  public CaptureReservationResponse captureReservation(CaptureReservation captureReservation) {
    AccountId accountId = captureReservation.accountId();
    ReservationId reservationId = captureReservation.reservationId();
    Instant occurredAt = Instant.now(clock);

    Account account = loadAccount(accountId);
    ReservationOutcome reservationOutcome =
        account.handle(captureReservation, eventIdGenerator, occurredAt);

    if (!reservationOutcome.hasEffect()) {
      return handleCaptureNoEffect(accountId, reservationId);
    }

    List<AccountEvent> captureReservationEvents = reservationOutcome.events();

    long versionBeforeEvents = versionBeforeEvents(account, captureReservationEvents);
    saveEvents(versionBeforeEvents, captureReservationEvents, accountId);
    processedReservationStore.updatePhase(
        accountId, reservationId, ReservationPhase.CAPTURED, occurredAt);
    return new CaptureReservationResponse(
        accountId,
        captureReservation.reservationId(),
        ReservationCaptureStatus.CAPTURED,
        Optional.of(reservationOutcome.capturedMoney()));
  }

  private TransactionStatus processTransaction(
      AccountTransaction command, BiFunction<Account, Instant, HandleResult> handler) {
    Instant now = Instant.now(clock);
    TransactionStatus status =
        processedTransactionStore.register(command.accountId(), command.transactionId(), now);
    if (status == TransactionStatus.NO_EFFECT) {
      return status;
    }
    Account account = loadAccount(command.accountId());
    List<AccountEvent> events = handler.apply(account, now).events();
    long versionBeforeEvents = versionBeforeEvents(account, events);
    saveEvents(versionBeforeEvents, events, command.accountId());
    return status;
  }

  @Transactional
  public TransactionStatus creditAccount(CreditAccount creditAccount) {
    return processTransaction(
        creditAccount, (account, now) -> account.handle(creditAccount, eventIdGenerator, now));
  }

  @Transactional
  public TransactionStatus debitAccount(DebitAccount debitAccount) {
    return processTransaction(
        debitAccount, (account, now) -> account.handle(debitAccount, eventIdGenerator, now));
  }

  @Transactional
  public ReserveFundsResponse reserveFunds(ReserveFunds reserveFunds) {
    Instant now = Instant.now(clock);
    AccountId accountId = reserveFunds.accountId();
    TransactionId transactionId = reserveFunds.transactionId();
    TransactionStatus status = processedTransactionStore.register(accountId, transactionId, now);

    if (status == TransactionStatus.NO_EFFECT) {
      ReservationId reservationId = processedReservationStore.lookup(accountId, transactionId);
      return new ReserveFundsResponse(reservationId, ReserveFundsStatus.NO_EFFECT);
    } else {
      ReservationId reservationId = reservationIdGenerator.newId();

      Account account = loadAccount(accountId);
      List<AccountEvent> reservationEvents =
          account.handle(reserveFunds, eventIdGenerator, reservationId, now).events();
      long versionBeforeEvents = versionBeforeEvents(account, reservationEvents);
      saveEvents(versionBeforeEvents, reservationEvents, accountId);

      processedReservationStore.register(
          accountId, transactionId, reservationId, ReservationPhase.RESERVED, now);

      return new ReserveFundsResponse(reservationId, ReserveFundsStatus.RESERVED);
    }
  }

  @Transactional
  public CancelReservationResponse cancelReservation(CancelReservation cancelReservation) {
    AccountId accountId = cancelReservation.accountId();
    ReservationId reservationId = cancelReservation.reservationId();
    Instant occurredAt = Instant.now(clock);

    Account account = loadAccount(accountId);
    ReservationOutcome reservationOutcome =
        account.handle(cancelReservation, eventIdGenerator, occurredAt);

    if (!reservationOutcome.hasEffect()) {
      return handleCancelNoEffect(accountId, reservationId);
    }

    List<AccountEvent> cancelReservationEvents = reservationOutcome.events();

    long versionBeforeEvents = versionBeforeEvents(account, cancelReservationEvents);
    saveEvents(versionBeforeEvents, cancelReservationEvents, accountId);
    processedReservationStore.updatePhase(
        accountId, reservationId, ReservationPhase.CANCELED, occurredAt);
    return new CancelReservationResponse(
        accountId,
        reservationId,
        Optional.of(reservationOutcome.capturedMoney()),
        CancelReservationStatus.CANCELED);
  }

  private CancelReservationResponse handleCancelNoEffect(
      AccountId accountId, ReservationId reservationId) {
    Optional<ReservationPhase> lookup = processedReservationStore.lookup(accountId, reservationId);

    if (lookup.isEmpty()) {
      throw new ReservationNotFoundException(reservationId);
    } else {
      return switch (lookup.get()) {
        case CANCELED ->
            new CancelReservationResponse(
                accountId, reservationId, Optional.empty(), CancelReservationStatus.NO_EFFECT);
        case CAPTURED -> throw new ReservationAlreadyCapturedException(reservationId);
        case RESERVED ->
            throw new IllegalStateException(
                "Should not happen: when reservation is reserved, cancel should not return no effect.");
      };
    }
  }
}
