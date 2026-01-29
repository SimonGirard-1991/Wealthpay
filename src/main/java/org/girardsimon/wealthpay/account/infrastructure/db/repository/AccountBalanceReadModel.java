package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.girardsimon.wealthpay.account.jooq.tables.AccountBalanceView.ACCOUNT_BALANCE_VIEW;

import java.math.BigDecimal;
import java.util.List;
import org.girardsimon.wealthpay.account.application.AccountBalanceProjector;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCancelled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.exception.AccountBalanceNotFoundException;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountBalanceViewEntryToDomainMapper;
import org.girardsimon.wealthpay.account.jooq.tables.records.AccountBalanceViewRecord;
import org.jooq.DSLContext;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class AccountBalanceReadModel implements AccountBalanceProjector {

  private final DSLContext dslContext;

  private final AccountBalanceViewEntryToDomainMapper accountBalanceViewEntryToDomainMapper;

  public AccountBalanceReadModel(
      DSLContext dslContext,
      AccountBalanceViewEntryToDomainMapper accountBalanceViewEntryToDomainMapper) {
    this.dslContext = dslContext;
    this.accountBalanceViewEntryToDomainMapper = accountBalanceViewEntryToDomainMapper;
  }

  private static void applyEventToState(AccountEvent event, ProjectionState currentState) {
    switch (event) {
      case AccountOpened opened -> {
        currentState.balance = opened.initialBalance().amount();
        currentState.currency = opened.currency().name();
        currentState.status = AccountStatus.OPENED.name();
      }
      case FundsCredited credited ->
          currentState.balance = currentState.balance.add(credited.money().amount());
      case FundsDebited debited ->
          currentState.balance = currentState.balance.subtract(debited.money().amount());
      case AccountClosed _ -> currentState.status = AccountStatus.CLOSED.name();
      case FundsReserved fundsReserved ->
          currentState.reserved = currentState.reserved.add(fundsReserved.money().amount());
      case ReservationCancelled reservationCancelled ->
          currentState.reserved =
              currentState.reserved.subtract(reservationCancelled.money().amount());
      case ReservationCaptured reservationCaptured -> {
        currentState.reserved =
            currentState.reserved.subtract(reservationCaptured.money().amount());
        currentState.balance = currentState.balance.subtract(reservationCaptured.money().amount());
      }
    }

    currentState.version = event.version();
  }

  @Override
  public AccountBalanceView getAccountBalance(AccountId accountId) {
    return dslContext
        .select(
            ACCOUNT_BALANCE_VIEW.ACCOUNT_ID,
            ACCOUNT_BALANCE_VIEW.BALANCE,
            ACCOUNT_BALANCE_VIEW.RESERVED,
            ACCOUNT_BALANCE_VIEW.CURRENCY,
            ACCOUNT_BALANCE_VIEW.STATUS,
            ACCOUNT_BALANCE_VIEW.VERSION)
        .from(ACCOUNT_BALANCE_VIEW)
        .where(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID.eq(accountId.id()))
        .fetchOptional()
        .map(accountBalanceViewEntryToDomainMapper)
        .orElseThrow(() -> new AccountBalanceNotFoundException(accountId));
  }

  @Override
  public void project(List<AccountEvent> events) {
    if (events.isEmpty()) {
      return;
    }

    AccountId accountId = events.getFirst().accountId();

    ProjectionState currentState =
        dslContext
            .select(
                ACCOUNT_BALANCE_VIEW.ACCOUNT_ID,
                ACCOUNT_BALANCE_VIEW.BALANCE,
                ACCOUNT_BALANCE_VIEW.RESERVED,
                ACCOUNT_BALANCE_VIEW.CURRENCY,
                ACCOUNT_BALANCE_VIEW.STATUS,
                ACCOUNT_BALANCE_VIEW.VERSION)
            .from(ACCOUNT_BALANCE_VIEW)
            .where(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID.eq(accountId.id()))
            .fetchOptional()
            .map(
                currentRecord ->
                    new ProjectionState(
                        currentRecord.get(ACCOUNT_BALANCE_VIEW.BALANCE),
                        currentRecord.get(ACCOUNT_BALANCE_VIEW.RESERVED),
                        currentRecord.get(ACCOUNT_BALANCE_VIEW.CURRENCY),
                        currentRecord.get(ACCOUNT_BALANCE_VIEW.STATUS),
                        currentRecord.get(ACCOUNT_BALANCE_VIEW.VERSION)))
            .orElseGet(ProjectionState::init);

    events.forEach(
        event -> {
          long expectedNextVersion = currentState.version + 1;
          if (event.version() != expectedNextVersion) {
            throw new OptimisticLockingFailureException(
                "Non contiguous versions for account %s: expected %d but got %d"
                    .formatted(accountId.id(), currentState.version, event.version()));
          }
          applyEventToState(event, currentState);
        });

    AccountBalanceViewRecord row = dslContext.newRecord(ACCOUNT_BALANCE_VIEW);
    row.setAccountId(accountId.id());
    row.setCurrency(currentState.currency);
    row.setBalance(currentState.balance);
    row.setReserved(currentState.reserved);
    row.setStatus(currentState.status);
    row.setVersion(currentState.version);

    int affectedRows =
        dslContext
            .insertInto(ACCOUNT_BALANCE_VIEW)
            .set(row)
            .onConflict(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID)
            .doUpdate()
            .set(row)
            .where(ACCOUNT_BALANCE_VIEW.VERSION.lt(currentState.version))
            .execute();

    if (affectedRows == 0) {
      throw new OptimisticLockingFailureException(
          "Concurrent update detected for account %s".formatted(accountId.id()));
    }
  }

  private static final class ProjectionState {
    BigDecimal balance;
    BigDecimal reserved;
    String currency;
    String status;
    long version;

    ProjectionState(
        BigDecimal balance, BigDecimal reserved, String currency, String status, long version) {
      this.balance = balance;
      this.reserved = reserved;
      this.currency = currency;
      this.status = status;
      this.version = version;
    }

    static ProjectionState init() {
      return new ProjectionState(BigDecimal.ZERO, BigDecimal.ZERO, null, null, 0L);
    }
  }
}
