package org.girardsimon.wealthpay.account.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;
import org.girardsimon.wealthpay.account.application.response.CaptureReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReservationCaptureStatus;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.command.AccountTransaction;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountApplicationService {

  private final AccountEventStore accountEventStore;
  private final AccountBalanceReader accountBalanceReader;
  private final AccountEventPublisher accountEventPublisher;
  private final ProcessedTransactionStore processedTransactionStore;
  private final Clock clock;
  private final AccountIdGenerator accountIdGenerator;
  private final EventIdGenerator eventIdGenerator;

  public AccountApplicationService(
      AccountEventStore accountEventStore,
      AccountBalanceReader accountBalanceReader,
      AccountEventPublisher accountEventPublisher,
      ProcessedTransactionStore processedTransactionStore,
      Clock clock,
      AccountIdGenerator accountIdGenerator,
      EventIdGenerator eventIdGenerator) {
    this.accountEventStore = accountEventStore;
    this.accountBalanceReader = accountBalanceReader;
    this.accountEventPublisher = accountEventPublisher;
    this.processedTransactionStore = processedTransactionStore;
    this.clock = clock;
    this.accountIdGenerator = accountIdGenerator;
    this.eventIdGenerator = eventIdGenerator;
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
    List<AccountEvent> createdAccountEvents =
        Account.handle(openAccount, accountId, eventIdGenerator, Instant.now(clock));
    saveEvents(0L, createdAccountEvents, accountId);
    return accountId;
  }

  @Transactional(readOnly = true)
  public AccountBalanceView getAccountBalance(AccountId accountId) {
    return accountBalanceReader.getAccountBalance(accountId);
  }

  @Transactional
  public CaptureReservationResponse captureReservation(CaptureReservation captureReservation) {
    AccountId accountId = captureReservation.accountId();
    Account account = loadAccount(accountId);

    List<AccountEvent> captureReservationEvents =
        account.handle(captureReservation, eventIdGenerator, Instant.now(clock));

    ReservationCaptured reservationCaptured =
        captureReservationEvents.stream()
            .filter(ReservationCaptured.class::isInstance)
            .map(ReservationCaptured.class::cast)
            .findFirst()
            .orElse(null);

    if (reservationCaptured == null) {
      return new CaptureReservationResponse(
          accountId, captureReservation.reservationId(), ReservationCaptureStatus.NO_EFFECT, null);
    }

    long versionBeforeEvents = versionBeforeEvents(account, captureReservationEvents);
    saveEvents(versionBeforeEvents, captureReservationEvents, accountId);
    return new CaptureReservationResponse(
        accountId,
        captureReservation.reservationId(),
        ReservationCaptureStatus.CAPTURED,
        reservationCaptured.money());
  }

  private TransactionStatus processTransaction(
      AccountTransaction command, BiFunction<Account, Instant, List<AccountEvent>> handler) {
    Instant now = Instant.now(clock);
    TransactionStatus status =
        processedTransactionStore.register(command.accountId(), command.transactionId(), now);
    if (status == TransactionStatus.NO_EFFECT) {
      return status;
    }
    Account account = loadAccount(command.accountId());
    List<AccountEvent> events = handler.apply(account, now);
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
}
