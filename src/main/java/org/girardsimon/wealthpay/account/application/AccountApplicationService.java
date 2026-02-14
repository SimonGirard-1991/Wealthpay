package org.girardsimon.wealthpay.account.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.girardsimon.wealthpay.account.application.response.CaptureReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReservationCaptureStatus;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.exception.AccountHistoryNotFound;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountApplicationService {

  private final AccountEventStore accountEventStore;
  private final AccountBalanceProjector accountBalanceProjector;
  private final AccountEventPublisher accountEventPublisher;
  private final Clock clock;
  private final AccountIdGenerator accountIdGenerator;
  private final EventIdGenerator eventIdGenerator;

  public AccountApplicationService(
      AccountEventStore accountEventStore,
      AccountBalanceProjector accountBalanceProjector,
      AccountEventPublisher accountEventPublisher,
      Clock clock,
      AccountIdGenerator accountIdGenerator,
      EventIdGenerator eventIdGenerator) {
    this.accountEventStore = accountEventStore;
    this.accountBalanceProjector = accountBalanceProjector;
    this.accountEventPublisher = accountEventPublisher;
    this.clock = clock;
    this.accountIdGenerator = accountIdGenerator;
    this.eventIdGenerator = eventIdGenerator;
  }

  private static long versionBeforeEvents(Account account, List<AccountEvent> events) {
    return account.getVersion() - events.size();
  }

  @Transactional
  public AccountId openAccount(OpenAccount openAccount) {
    AccountId accountId = accountIdGenerator.newId();

    long expectedVersion = 0L;
    List<AccountEvent> createdAccountEvents =
        Account.handle(openAccount, accountId, eventIdGenerator, Instant.now(clock));
    accountEventStore.appendEvents(accountId, expectedVersion, createdAccountEvents);
    accountEventPublisher.publish(createdAccountEvents);
    return accountId;
  }

  @Transactional(readOnly = true)
  public AccountBalanceView getAccountBalance(AccountId accountId) {
    return accountBalanceProjector.getAccountBalance(accountId);
  }

  @Transactional
  public CaptureReservationResponse captureReservation(CaptureReservation captureReservation) {
    AccountId accountId = captureReservation.accountId();
    List<AccountEvent> history = accountEventStore.loadEvents(accountId);
    if (history.isEmpty()) {
      throw new AccountHistoryNotFound();
    }
    Account account = Account.rehydrate(history);
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
    accountEventStore.appendEvents(accountId, versionBeforeEvents, captureReservationEvents);
    accountEventPublisher.publish(captureReservationEvents);
    return new CaptureReservationResponse(
        accountId,
        captureReservation.reservationId(),
        ReservationCaptureStatus.CAPTURED,
        reservationCaptured.money());
  }
}
