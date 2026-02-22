package org.girardsimon.wealthpay.account.domain.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.girardsimon.wealthpay.account.domain.command.CancelReservation;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.command.CloseAccount;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCanceled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.exception.AccountCurrencyMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountHistoryNotFoundException;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.exception.AccountNotEmptyException;
import org.girardsimon.wealthpay.account.domain.exception.AmountMustBePositiveException;
import org.girardsimon.wealthpay.account.domain.exception.InsufficientFundsException;
import org.girardsimon.wealthpay.account.domain.exception.InvalidAccountEventStreamException;
import org.girardsimon.wealthpay.account.domain.exception.InvalidInitialBalanceException;

public class Account {
  private final AccountId id;
  private final SupportedCurrency currency;
  private final Map<ReservationId, Money> reservations = new HashMap<>();
  private Money balance;
  private AccountStatus status;
  private long version;

  private Account(AccountId id, SupportedCurrency currency) {
    this.id = id;
    this.currency = currency;
  }

  public static HandleResult handle(
      OpenAccount openAccount,
      AccountId accountId,
      EventIdGenerator eventIdGenerator,
      Instant occurredAt) {
    Money initialBalance = openAccount.initialBalance();
    if (initialBalance.isStrictlyNegative()) {
      throw new InvalidInitialBalanceException(initialBalance);
    }
    SupportedCurrency accountCurrency = openAccount.accountCurrency();
    if (!initialBalance.currency().equals(accountCurrency)) {
      throw new AccountCurrencyMismatchException(
          initialBalance.currency().name(), accountCurrency.name());
    }
    AccountEventMeta meta =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, occurredAt, 1L);
    AccountOpened accountOpened = new AccountOpened(meta, accountCurrency, initialBalance);
    return new SimpleCommandResult(List.of(accountOpened));
  }

  private static void ensureStrictlyPositiveAmount(Money amount) {
    if (amount.isNegativeOrZero()) {
      throw new AmountMustBePositiveException(amount);
    }
  }

  public static Account rehydrate(List<AccountEvent> history) {
    if (history == null || history.isEmpty()) {
      throw new AccountHistoryNotFoundException();
    }
    AccountEvent firstEvent = history.getFirst();
    if (!(firstEvent instanceof AccountOpened accountOpened)) {
      throw new InvalidAccountEventStreamException(
          "Account history must start with AccountOpened event");
    }
    Account account = new Account(accountOpened.accountId(), accountOpened.currency());
    history.forEach(account::apply);
    return account;
  }

  public HandleResult handle(
      CreditAccount creditAccount, EventIdGenerator eventIdGenerator, Instant occurredAt) {
    ensureAccountIdConsistency(creditAccount.accountId());
    ensureCurrencyConsistency(creditAccount.money().currency());
    ensureStrictlyPositiveAmount(creditAccount.money());
    ensureActive();
    AccountEventMeta meta =
        AccountEventMeta.of(
            eventIdGenerator.newId(), creditAccount.accountId(), occurredAt, this.version + 1);
    FundsCredited fundsCredited =
        new FundsCredited(meta, creditAccount.transactionId(), creditAccount.money());
    apply(fundsCredited);
    return new SimpleCommandResult(List.of(fundsCredited));
  }

  public HandleResult handle(
      DebitAccount debitAccount, EventIdGenerator eventIdGenerator, Instant occurredAt) {
    ensureAccountIdConsistency(debitAccount.accountId());
    ensureCurrencyConsistency(debitAccount.money().currency());
    ensureStrictlyPositiveAmount(debitAccount.money());
    ensureActive();
    if (debitAccount.money().isGreaterThan(getAvailableBalance())) {
      throw new InsufficientFundsException();
    }
    AccountEventMeta meta =
        AccountEventMeta.of(
            eventIdGenerator.newId(), debitAccount.accountId(), occurredAt, this.version + 1);
    FundsDebited fundsDebited =
        new FundsDebited(meta, debitAccount.transactionId(), debitAccount.money());
    apply(fundsDebited);
    return new SimpleCommandResult(List.of(fundsDebited));
  }

  public HandleResult handle(
      ReserveFunds reserveFunds,
      EventIdGenerator eventIdGenerator,
      ReservationId reservationId,
      Instant occurredAt) {
    ensureAccountIdConsistency(reserveFunds.accountId());
    ensureCurrencyConsistency(reserveFunds.money().currency());
    ensureStrictlyPositiveAmount(reserveFunds.money());
    ensureActive();

    if (reserveFunds.money().isGreaterThan(getAvailableBalance())) {
      throw new InsufficientFundsException();
    }
    AccountEventMeta meta =
        AccountEventMeta.of(
            eventIdGenerator.newId(), reserveFunds.accountId(), occurredAt, this.version + 1);
    FundsReserved fundsReserved =
        new FundsReserved(meta, reserveFunds.transactionId(), reservationId, reserveFunds.money());
    apply(fundsReserved);
    return new SimpleCommandResult(List.of(fundsReserved));
  }

  public ReservationOutcome handle(
      CancelReservation cancelReservation, EventIdGenerator eventIdGenerator, Instant occurredAt) {
    ensureAccountIdConsistency(cancelReservation.accountId());
    ensureActive();
    if (!this.reservations.containsKey(cancelReservation.reservationId())) {
      return new ReservationOutcome(List.of(), Optional.empty());
    }
    AccountEventMeta meta =
        AccountEventMeta.of(
            eventIdGenerator.newId(), cancelReservation.accountId(), occurredAt, this.version + 1);
    Money money = this.reservations.get(cancelReservation.reservationId());
    ReservationCanceled reservationCanceled =
        new ReservationCanceled(meta, cancelReservation.reservationId(), money);
    apply(reservationCanceled);
    return new ReservationOutcome(List.of(reservationCanceled), Optional.of(money));
  }

  public HandleResult handle(
      CloseAccount closeAccount, EventIdGenerator eventIdGenerator, Instant occurredAt) {
    ensureAccountIdConsistency(closeAccount.accountId());
    ensureActive();
    if (!this.balance.isZero() || !this.reservations.isEmpty()) {
      throw new AccountNotEmptyException();
    }
    AccountEventMeta meta =
        AccountEventMeta.of(
            eventIdGenerator.newId(), closeAccount.accountId(), occurredAt, this.version + 1);
    AccountClosed accountClosed = new AccountClosed(meta);
    apply(accountClosed);
    return new SimpleCommandResult(List.of(accountClosed));
  }

  public ReservationOutcome handle(
      CaptureReservation captureReservation,
      EventIdGenerator eventIdGenerator,
      Instant occurredAt) {
    ensureAccountIdConsistency(captureReservation.accountId());
    ensureActive();
    Money money = this.reservations.get(captureReservation.reservationId());
    if (money == null) {
      return new ReservationOutcome(List.of(), Optional.empty());
    }
    AccountEventMeta meta =
        AccountEventMeta.of(
            eventIdGenerator.newId(), captureReservation.accountId(), occurredAt, this.version + 1);
    ReservationCaptured reservationCaptured =
        new ReservationCaptured(meta, captureReservation.reservationId(), money);
    apply(reservationCaptured);
    return new ReservationOutcome(List.of(reservationCaptured), Optional.of(money));
  }

  private void ensureAccountIdConsistency(AccountId accountId) {
    if (!accountId.equals(this.id)) {
      throw new AccountIdMismatchException(accountId, this.id);
    }
  }

  private void ensureCurrencyConsistency(SupportedCurrency currency) {
    if (!currency.equals(this.currency)) {
      throw new AccountCurrencyMismatchException(this.currency.name(), currency.name());
    }
  }

  private void ensureActive() {
    if (this.status != AccountStatus.OPENED) {
      throw new AccountInactiveException();
    }
  }

  private void apply(AccountEvent accountEvent) {
    this.version = accountEvent.version();

    switch (accountEvent) {
      case AccountOpened accountOpened -> {
        this.balance = accountOpened.initialBalance();
        this.status = AccountStatus.OPENED;
      }
      case FundsCredited fundsCredited -> this.balance = this.balance.add(fundsCredited.money());
      case AccountClosed _ -> this.status = AccountStatus.CLOSED;
      case FundsDebited fundsDebited -> this.balance = this.balance.subtract(fundsDebited.money());
      case FundsReserved fundsReserved ->
          this.reservations.put(fundsReserved.reservationId(), fundsReserved.money());
      case ReservationCanceled reservationCanceled ->
          this.reservations.remove(reservationCanceled.reservationId());
      case ReservationCaptured reservationCaptured -> {
        this.balance = this.balance.subtract(reservationCaptured.money());
        this.reservations.remove(reservationCaptured.reservationId());
      }
    }
  }

  public Money getAvailableBalance() {
    return balance.subtract(totalReservedFunds());
  }

  private Money totalReservedFunds() {
    return reservations.values().stream().reduce(Money.zero(currency), Money::add);
  }

  public AccountId getId() {
    return id;
  }

  public Money getBalance() {
    return balance;
  }

  public AccountStatus getStatus() {
    return status;
  }

  public SupportedCurrency getCurrency() {
    return currency;
  }

  public long getVersion() {
    return version;
  }

  public Map<ReservationId, Money> getReservations() {
    return Map.copyOf(reservations);
  }
}
