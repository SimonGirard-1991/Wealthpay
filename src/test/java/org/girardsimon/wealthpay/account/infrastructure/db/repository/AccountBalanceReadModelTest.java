package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.girardsimon.wealthpay.account.jooq.tables.AccountBalanceView.ACCOUNT_BALANCE_VIEW;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCancelled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountBalanceViewEntryToDomainMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

@JooqTest
@Import({AccountBalanceReadModel.class, AccountBalanceViewEntryToDomainMapper.class})
class AccountBalanceReadModelTest extends AbstractContainerTest {

  @Autowired private DSLContext dslContext;

  @Autowired private AccountBalanceReadModel accountBalanceReadModel;

  @Test
  void getAccountBalance_should_returns_expected_account_balance_view() {
    // Arrange
    AccountId accountId = AccountId.newId();
    String currency = "USD";
    BigDecimal balance = BigDecimal.valueOf(100.5000).setScale(4, RoundingMode.HALF_UP);
    BigDecimal reserved = BigDecimal.valueOf(0.0000).setScale(4, RoundingMode.HALF_UP);
    String status = "OPENED";
    long version = 1L;
    dslContext
        .insertInto(ACCOUNT_BALANCE_VIEW)
        .set(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID, accountId.id())
        .set(ACCOUNT_BALANCE_VIEW.CURRENCY, currency)
        .set(ACCOUNT_BALANCE_VIEW.BALANCE, balance)
        .set(ACCOUNT_BALANCE_VIEW.RESERVED, reserved)
        .set(ACCOUNT_BALANCE_VIEW.STATUS, status)
        .set(ACCOUNT_BALANCE_VIEW.VERSION, version)
        .execute();

    // Act
    AccountBalanceView accountBalance = accountBalanceReadModel.getAccountBalance(accountId);

    // Assert
    assertAll(
        () -> assertThat(accountBalance.accountId()).isEqualTo(accountId),
        () ->
            assertThat(accountBalance.balance().amount())
                .isEqualTo(BigDecimal.valueOf(100.50).setScale(2, RoundingMode.HALF_EVEN)),
        () -> assertThat(accountBalance.balance().currency()).isEqualTo(SupportedCurrency.USD),
        () ->
            assertThat(accountBalance.reservedFunds().amount())
                .isEqualTo(BigDecimal.valueOf(0.00).setScale(2, RoundingMode.HALF_EVEN)),
        () ->
            assertThat(accountBalance.reservedFunds().currency()).isEqualTo(SupportedCurrency.USD),
        () -> assertThat(accountBalance.status()).isEqualTo(status),
        () -> assertThat(accountBalance.version()).isEqualTo(version));
  }

  @Test
  void project_update_account_balance_view_as_expected() {
    // Arrange
    AccountId accountId = AccountId.newId();
    AccountOpened accountOpened =
        new AccountOpened(
            accountId,
            Instant.now(),
            1L,
            SupportedCurrency.USD,
            Money.of(BigDecimal.valueOf(1000L), SupportedCurrency.USD));
    FundsCredited fundsCredited =
        new FundsCredited(
            TransactionId.newId(),
            accountId,
            Instant.now(),
            2L,
            Money.of(BigDecimal.valueOf(500L), SupportedCurrency.USD));
    ReservationId reservationId = ReservationId.newId();
    Money moneyReserved = Money.of(BigDecimal.valueOf(200L), SupportedCurrency.USD);
    FundsReserved fundsReserved =
        new FundsReserved(accountId, Instant.now(), 3L, reservationId, moneyReserved);
    ReservationCancelled reservationCancelled =
        new ReservationCancelled(accountId, Instant.now(), 4L, reservationId, moneyReserved);
    FundsDebited fundsDebited =
        new FundsDebited(
            TransactionId.newId(),
            accountId,
            Instant.now(),
            5L,
            Money.of(BigDecimal.valueOf(1500L), SupportedCurrency.USD));
    AccountClosed accountClosed = new AccountClosed(accountId, Instant.now(), 6L);
    List<AccountEvent> events =
        List.of(
            accountOpened,
            fundsCredited,
            fundsReserved,
            reservationCancelled,
            fundsDebited,
            accountClosed);

    // Act
    accountBalanceReadModel.project(events);

    // Assert
    AccountBalanceView accountBalance = accountBalanceReadModel.getAccountBalance(accountId);
    assertAll(
        () -> assertThat(accountBalance.accountId()).isEqualTo(accountId),
        () ->
            assertThat(accountBalance.balance().amount())
                .isEqualTo(BigDecimal.valueOf(0.00).setScale(2, RoundingMode.HALF_EVEN)),
        () -> assertThat(accountBalance.balance().currency()).isEqualTo(SupportedCurrency.USD),
        () ->
            assertThat(accountBalance.reservedFunds().amount())
                .isEqualTo(BigDecimal.valueOf(0.00).setScale(2, RoundingMode.HALF_EVEN)),
        () ->
            assertThat(accountBalance.reservedFunds().currency()).isEqualTo(SupportedCurrency.USD),
        () -> assertThat(accountBalance.status()).isEqualTo("CLOSED"),
        () -> assertThat(accountBalance.version()).isEqualTo(6L));
  }

  @Test
  void account_balance_reservation_lifecycle() {
    // Arrange
    AccountId accountId = AccountId.newId();
    AccountOpened accountOpened =
        new AccountOpened(
            accountId,
            Instant.now(),
            1L,
            SupportedCurrency.USD,
            Money.of(BigDecimal.valueOf(1000L), SupportedCurrency.USD));
    ReservationId reservationId = ReservationId.newId();
    Money moneyReserved = Money.of(BigDecimal.valueOf(200L), SupportedCurrency.USD);
    FundsReserved fundsReserved =
        new FundsReserved(accountId, Instant.now(), 2L, reservationId, moneyReserved);
    ReservationCaptured reservationCaptured =
        new ReservationCaptured(accountId, reservationId, moneyReserved, 3L, Instant.now());

    List<AccountEvent> events = List.of(accountOpened, fundsReserved, reservationCaptured);

    // Act
    accountBalanceReadModel.project(events);

    // Assert
    AccountBalanceView accountBalance = accountBalanceReadModel.getAccountBalance(accountId);
    assertAll(
        () -> assertThat(accountBalance.accountId()).isEqualTo(accountId),
        () ->
            assertThat(accountBalance.balance().amount())
                .isEqualTo(BigDecimal.valueOf(800.00).setScale(2, RoundingMode.HALF_EVEN)),
        () -> assertThat(accountBalance.balance().currency()).isEqualTo(SupportedCurrency.USD),
        () ->
            assertThat(accountBalance.reservedFunds().amount())
                .isEqualTo(BigDecimal.valueOf(0.00).setScale(2, RoundingMode.HALF_EVEN)),
        () ->
            assertThat(accountBalance.reservedFunds().currency()).isEqualTo(SupportedCurrency.USD),
        () -> assertThat(accountBalance.status()).isEqualTo("OPENED"),
        () -> assertThat(accountBalance.version()).isEqualTo(3L));
  }

  @Test
  void project_should_throw_optimistic_lock_exception_when_expected_version_is_outdated() {
    // Arrange
    UUID accountId = UUID.randomUUID();
    String currency = "USD";
    BigDecimal balance = BigDecimal.valueOf(1000L);
    BigDecimal reserved = BigDecimal.ZERO;
    String status = "OPENED";
    long version = 55L;
    dslContext
        .insertInto(ACCOUNT_BALANCE_VIEW)
        .set(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID, accountId)
        .set(ACCOUNT_BALANCE_VIEW.CURRENCY, currency)
        .set(ACCOUNT_BALANCE_VIEW.BALANCE, balance)
        .set(ACCOUNT_BALANCE_VIEW.RESERVED, reserved)
        .set(ACCOUNT_BALANCE_VIEW.STATUS, status)
        .set(ACCOUNT_BALANCE_VIEW.VERSION, version)
        .execute();

    FundsCredited fundsCredited =
        new FundsCredited(
            TransactionId.newId(),
            AccountId.of(accountId),
            Instant.now(),
            version - 1,
            Money.of(BigDecimal.valueOf(500L), SupportedCurrency.USD));

    List<AccountEvent> events = List.of(fundsCredited);

    // Act ... Assert
    assertThatExceptionOfType(OptimisticLockingFailureException.class)
        .isThrownBy(() -> accountBalanceReadModel.project(events));
  }
}
