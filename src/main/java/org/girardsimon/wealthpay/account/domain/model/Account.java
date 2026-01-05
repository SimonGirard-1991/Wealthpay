package org.girardsimon.wealthpay.account.domain.model;

import org.girardsimon.wealthpay.account.domain.command.CancelReservation;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.command.CloseAccount;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCancelled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.exception.AccountCurrencyMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountHistoryNotFound;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.exception.AccountNotEmptyException;
import org.girardsimon.wealthpay.account.domain.exception.AmountMustBePositiveException;
import org.girardsimon.wealthpay.account.domain.exception.InsufficientFundsException;
import org.girardsimon.wealthpay.account.domain.exception.InvalidAccountEventStreamException;
import org.girardsimon.wealthpay.account.domain.exception.InvalidInitialBalanceException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationConflictException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Account {
    private final AccountId id;
    private final SupportedCurrency currency;
    private Money balance;
    private AccountStatus status;
    private final Map<ReservationId, Money> reservations = new HashMap<>();
    private long version;

    private Account(AccountId id, SupportedCurrency currency) {
        this.id = id;
        this.currency = currency;
    }

    public static List<AccountEvent> handle(OpenAccount openAccount, AccountId accountId, long version, Instant occurredAt) {
        Money initialBalance = openAccount.initialBalance();
        if(initialBalance.isStrictlyNegative()) {
            throw new InvalidInitialBalanceException(initialBalance);
        }
        SupportedCurrency accountCurrency = openAccount.accountCurrency();
        if(!initialBalance.currency().equals(accountCurrency)) {
            throw new AccountCurrencyMismatchException(initialBalance.currency().name() ,
                    accountCurrency.name());
        }
        AccountOpened accountOpened = new AccountOpened(
                accountId,
                occurredAt,
                version,
                accountCurrency,
                initialBalance
        );
        return List.of(accountOpened);
    }

    public List<AccountEvent> handle(CreditAccount creditAccount, Instant occurredAt) {
        ensureAccountIdConsistency(creditAccount.accountId());
        checkCurrencyConsistency(creditAccount.amount().currency());
        checkStrictlyPositiveAmount(creditAccount.amount());
        ensureActive();
        FundsCredited fundsCredited = new FundsCredited(
                creditAccount.transactionId(),
                creditAccount.accountId(),
                occurredAt,
                this.version + 1,
                creditAccount.amount()
        );
        return List.of(fundsCredited);
    }

    private static void checkStrictlyPositiveAmount(Money amount) {
        if(amount.isNegativeOrZero()) {
            throw new AmountMustBePositiveException(amount);
        }
    }

    public List<AccountEvent> handle(DebitAccount debitAccount, Instant occurredAt) {
        ensureAccountIdConsistency(debitAccount.accountId());
        checkCurrencyConsistency(debitAccount.amount().currency());
        checkStrictlyPositiveAmount(debitAccount.amount());
        ensureActive();
        if(debitAccount.amount().isGreaterThan(getAvailableBalance())) {
            throw new InsufficientFundsException();
        }
        FundsDebited fundsDebited = new FundsDebited(
                debitAccount.transactionId(),
                debitAccount.accountId(),
                occurredAt,
                this.version + 1,
                debitAccount.amount()
        );
        return List.of(fundsDebited);
    }

    public List<AccountEvent> handle(ReserveFunds reserveFunds, Instant occurredAt) {
        ensureAccountIdConsistency(reserveFunds.accountId());
        checkCurrencyConsistency(reserveFunds.money().currency());
        checkStrictlyPositiveAmount(reserveFunds.money());
        ensureActive();

        Money existing = this.reservations.get(reserveFunds.reservationId());
        if(existing != null) {
            if(existing.equals(reserveFunds.money())) {
                return List.of();
            }
            throw new ReservationConflictException(reserveFunds.reservationId(), existing, reserveFunds.money());
        }
        if(reserveFunds.money().isGreaterThan(getAvailableBalance())) {
            throw new InsufficientFundsException();
        }
        FundsReserved fundsReserved = new FundsReserved(
                reserveFunds.accountId(),
                occurredAt,
                this.version + 1,
                reserveFunds.reservationId(),
                reserveFunds.money()
        );
        return List.of(fundsReserved);
    }

    public List<AccountEvent> handle(CancelReservation cancelReservation, Instant occurredAt) {
        ensureAccountIdConsistency(cancelReservation.accountId());
        ensureActive();
        if(!this.reservations.containsKey(cancelReservation.reservationId())) {
            return List.of();
        }
        ReservationCancelled reservationCancelled = new ReservationCancelled(
                cancelReservation.accountId(),
                occurredAt,
                this.version + 1,
                cancelReservation.reservationId(),
                this.reservations.get(cancelReservation.reservationId())
        );
        return List.of(reservationCancelled);
    }

    public List<AccountEvent> handle(CloseAccount closeAccount, Instant occurredAt) {
        ensureAccountIdConsistency(closeAccount.accountId());
        ensureActive();
        if(!this.balance.isAmountZero() || !this.reservations.isEmpty()) {
            throw new AccountNotEmptyException();
        }
        AccountClosed accountClosed = new AccountClosed(
                closeAccount.accountId(),
                occurredAt,
                this.version + 1
        );
        return List.of(accountClosed);
    }

    public List<AccountEvent> handle(CaptureReservation captureReservation, Instant occurredAt) {
        ensureAccountIdConsistency(captureReservation.accountId());
        ensureActive();
        Money money = this.reservations.get(captureReservation.reservationId());
        if(money == null) {
            return List.of();
        }
        ReservationCaptured reservationCaptured = new ReservationCaptured(
                captureReservation.accountId(),
                captureReservation.reservationId(),
                money,
                this.version + 1,
                occurredAt
        );
        return List.of(reservationCaptured);
    }

    private void ensureAccountIdConsistency(AccountId accountId) {
        if(!accountId.equals(this.id)) {
            throw new AccountIdMismatchException(accountId, this.id);
        }
    }

    private void checkCurrencyConsistency(SupportedCurrency currency) {
        if(!currency.equals(this.currency)) {
            throw new AccountCurrencyMismatchException(this.currency.name(), currency.name());
        }
    }

    private void ensureActive() {
        if(this.status != AccountStatus.OPENED) {
            throw new AccountInactiveException();
        }
    }

    public static Account rehydrate(List<AccountEvent> history) {
        if(history == null || history.isEmpty()) {
            throw new AccountHistoryNotFound();
        }
        AccountEvent firstEvent = history.getFirst();
        if(!(firstEvent instanceof AccountOpened accountOpened)) {
            throw new InvalidAccountEventStreamException("Account history must start with AccountOpened event");
        }
        Account account = new Account(accountOpened.accountId(), accountOpened.currency());
        history.forEach(account::apply);
        return account;
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
            case FundsReserved fundsReserved -> this.reservations.put(fundsReserved.reservationId(), fundsReserved.money());
            case ReservationCancelled reservationCancelled -> this.reservations.remove(reservationCancelled.reservationId());
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
        return reservations.values().stream()
                .reduce(Money.zero(currency), Money::add);
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
