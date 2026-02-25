package org.girardsimon.wealthpay.account.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.girardsimon.wealthpay.account.application.response.ReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReservationResult;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsResponse;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.domain.command.AccountTransaction;
import org.girardsimon.wealthpay.account.domain.command.CancelReservation;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.command.ReservationCommand;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountApplicationService {

  private static final Logger log = LoggerFactory.getLogger(AccountApplicationService.class);

  private final AccountEventStore accountEventStore;
  private final AccountEventPublisher accountEventPublisher;
  private final ProcessedTransactionStore processedTransactionStore;
  private final ProcessedReservationStore processedReservationStore;
  private final AccountSnapshotStore accountSnapshotStore;
  private final Clock clock;
  private final AccountIdGenerator accountIdGenerator;
  private final EventIdGenerator eventIdGenerator;
  private final ReservationIdGenerator reservationIdGenerator;
  private final int snapshotThreshold;

  public AccountApplicationService(
      AccountEventStore accountEventStore,
      AccountEventPublisher accountEventPublisher,
      ProcessedTransactionStore processedTransactionStore,
      ProcessedReservationStore processedReservationStore,
      AccountSnapshotStore accountSnapshotStore,
      Clock clock,
      AccountIdGenerator accountIdGenerator,
      EventIdGenerator eventIdGenerator,
      ReservationIdGenerator reservationIdGenerator,
      @Value("${account-event.snapshot.threshold}") int snapshotThreshold) {
    if (snapshotThreshold <= 0) {
      throw new IllegalArgumentException(
          "Property account-event.snapshot.threshold must be > 0 but was " + snapshotThreshold);
    }
    this.accountEventStore = accountEventStore;
    this.accountEventPublisher = accountEventPublisher;
    this.processedTransactionStore = processedTransactionStore;
    this.processedReservationStore = processedReservationStore;
    this.accountSnapshotStore = accountSnapshotStore;
    this.clock = clock;
    this.accountIdGenerator = accountIdGenerator;
    this.eventIdGenerator = eventIdGenerator;
    this.reservationIdGenerator = reservationIdGenerator;
    this.snapshotThreshold = snapshotThreshold;
  }

  private static long versionBeforeEvents(Account account, List<AccountEvent> events) {
    return account.getVersion() - events.size();
  }

  private Account loadAccount(AccountId accountId) {
    return accountSnapshotStore
        .load(accountId)
        .map(
            accountSnapshot -> {
              List<AccountEvent> eventsAfterSnapshot =
                  accountEventStore.loadEventsAfterVersion(accountId, accountSnapshot.version());
              return Account.rehydrateFromSnapshot(accountSnapshot, eventsAfterSnapshot);
            })
        .orElseGet(() -> Account.rehydrate(accountEventStore.loadEvents(accountId)));
  }

  private void saveEvents(Account account, long expectedVersion, List<AccountEvent> accountEvents) {
    persistEvents(account.getId(), expectedVersion, accountEvents);
    if (account.getVersion() / snapshotThreshold > expectedVersion / snapshotThreshold) {
      try {
        accountSnapshotStore.saveSnapshot(Account.toSnapshot(account));
      } catch (RuntimeException e) {
        // Snapshot is performance optimization and should not block a critical path
        log.warn("Failed to save snapshot for account {}", account.getId(), e);
      }
    }
  }

  private void persistEvents(
      AccountId accountId, long expectedVersion, List<AccountEvent> accountEvents) {
    accountEventStore.appendEvents(accountId, expectedVersion, accountEvents);
    accountEventPublisher.publish(accountEvents);
  }

  @Transactional
  public AccountId openAccount(OpenAccount openAccount) {
    AccountId accountId = accountIdGenerator.newId();
    HandleResult result =
        Account.handle(openAccount, accountId, eventIdGenerator, Instant.now(clock));
    persistEvents(accountId, 0L, result.events());
    return accountId;
  }

  @Transactional
  public ReservationResponse captureReservation(CaptureReservation captureReservation) {
    return processReservation(
        captureReservation,
        ReservationPhase.CAPTURED,
        ReservationResult.CAPTURED,
        ReservationPhase::ensureCanCapture,
        (account, now) -> account.handle(captureReservation, eventIdGenerator, now));
  }

  private TransactionStatus processTransaction(
      AccountTransaction command, BiFunction<Account, Instant, HandleResult> handler) {
    Instant now = Instant.now(clock);
    TransactionStatus status =
        processedTransactionStore.register(
            command.accountId(), command.transactionId(), command.fingerprint(), now);
    if (status == TransactionStatus.NO_EFFECT) {
      return status;
    }
    Account account = loadAccount(command.accountId());
    List<AccountEvent> events = handler.apply(account, now).events();
    long versionBeforeEvents = versionBeforeEvents(account, events);
    saveEvents(account, versionBeforeEvents, events);
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
    TransactionStatus status =
        processedTransactionStore.register(
            accountId, transactionId, reserveFunds.fingerprint(), now);

    if (status == TransactionStatus.NO_EFFECT) {
      ReservationId reservationId =
          processedReservationStore.lookupReservation(accountId, transactionId);
      return new ReserveFundsResponse(reservationId, ReservationResult.NO_EFFECT);
    } else {
      ReservationId reservationId = reservationIdGenerator.newId();

      Account account = loadAccount(accountId);
      List<AccountEvent> reservationEvents =
          account.handle(reserveFunds, eventIdGenerator, reservationId, now).events();
      long versionBeforeEvents = versionBeforeEvents(account, reservationEvents);
      saveEvents(account, versionBeforeEvents, reservationEvents);

      processedReservationStore.register(
          accountId, transactionId, reservationId, ReservationPhase.RESERVED, now);

      return new ReserveFundsResponse(reservationId, ReservationResult.RESERVED);
    }
  }

  @Transactional
  public ReservationResponse cancelReservation(CancelReservation cancelReservation) {
    return processReservation(
        cancelReservation,
        ReservationPhase.CANCELED,
        ReservationResult.CANCELED,
        ReservationPhase::ensureCanCancel,
        (account, now) -> account.handle(cancelReservation, eventIdGenerator, now));
  }

  private ReservationResponse processReservation(
      ReservationCommand reservationCommand,
      ReservationPhase targetPhase,
      ReservationResult expectedResult,
      Consumer<ReservationPhase> phaseValidator,
      BiFunction<Account, Instant, ReservationOutcome> handler) {
    AccountId accountId = reservationCommand.accountId();
    ReservationId reservationId = reservationCommand.reservationId();
    Instant occurredAt = Instant.now(clock);

    Account account = loadAccount(accountId);
    ReservationOutcome reservationOutcome = handler.apply(account, occurredAt);

    if (!reservationOutcome.hasEffect()) {
      return checkIdempotence(accountId, reservationId, phaseValidator);
    }

    List<AccountEvent> events = reservationOutcome.events();

    long versionBeforeEvents = versionBeforeEvents(account, events);
    saveEvents(account, versionBeforeEvents, events);
    processedReservationStore.updatePhase(accountId, reservationId, targetPhase, occurredAt);
    return new ReservationResponse(
        accountId, reservationId, reservationOutcome.reservedAmount(), expectedResult);
  }

  private ReservationResponse checkIdempotence(
      AccountId accountId, ReservationId reservationId, Consumer<ReservationPhase> phaseValidator) {
    ReservationPhase reservationPhase =
        processedReservationStore
            .lookupPhase(accountId, reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));

    if (reservationPhase == ReservationPhase.RESERVED) {
      throw new IllegalStateException(
          "Inconsistency: reservation %s is RESERVED in store but absent from aggregate"
              .formatted(reservationId));
    }
    phaseValidator.accept(reservationPhase);

    return new ReservationResponse(
        accountId, reservationId, Optional.empty(), ReservationResult.NO_EFFECT);
  }
}
