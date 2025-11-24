package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCancelled;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountBalanceViewEntryToDomainMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.girardsimon.wealthpay.account.jooq.tables.AccountBalanceView.ACCOUNT_BALANCE_VIEW;
import static org.junit.jupiter.api.Assertions.assertAll;

@JooqTest
@Import({
        AccountBalanceReadModel.class,
        AccountBalanceViewEntryToDomainMapper.class
})
class AccountBalanceReadModelTest extends AbstractContainerTest {

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private AccountBalanceReadModel accountBalanceReadModel;

    @Test
    void getAccountBalance_should_returns_expected_account_balance_view() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        String currency = "USD";
        BigDecimal balance = BigDecimal.valueOf(100.5000).setScale(4, RoundingMode.HALF_UP);
        BigDecimal reserved = BigDecimal.valueOf(0.0000).setScale(4, RoundingMode.HALF_UP);
        String status = "OPENED";
        long version = 1L;
        dslContext.insertInto(ACCOUNT_BALANCE_VIEW)
                .set(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID, accountId)
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
                () -> assertThat(accountBalance.currency()).isEqualTo(currency),
                () -> assertThat(accountBalance.balance()).isEqualTo(balance),
                () -> assertThat(accountBalance.reservedFunds()).isEqualTo(reserved),
                () -> assertThat(accountBalance.status()).isEqualTo(status),
                () -> assertThat(accountBalance.version()).isEqualTo(version )
        );
    }

    @Test
    void project_update_account_balance_view_as_expected() {
        // Arrange
        AccountId accountId = AccountId.newId();
        AccountOpened accountOpened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                SupportedCurrency.USD,
                Money.of(BigDecimal.valueOf(1000L), SupportedCurrency.USD)
        );
        FundsCredited fundsCredited = new FundsCredited(
                accountId,
                Instant.now(),
                2L,
                Money.of(BigDecimal.valueOf(500L), SupportedCurrency.USD)
        );
        ReservationId reservationId = ReservationId.newId();
        Money moneyReserved = Money.of(BigDecimal.valueOf(200L), SupportedCurrency.USD);
        FundsReserved fundsReserved = new FundsReserved(
                accountId,
                Instant.now(),
                3L,
                reservationId,
                moneyReserved
        );
        ReservationCancelled reservationCancelled = new ReservationCancelled(
                accountId,
                Instant.now(),
                4L,
                reservationId,
                moneyReserved
        );
        FundsDebited fundsDebited = new FundsDebited(
                accountId,
                Instant.now(),
                5L,
                Money.of(BigDecimal.valueOf(1500L), SupportedCurrency.USD)
        );
        AccountClosed accountClosed = new AccountClosed(
                accountId,
                Instant.now(),
                6L
        );
        List<AccountEvent> events = List.of(accountOpened, fundsCredited, fundsReserved, reservationCancelled, fundsDebited, accountClosed);

        // Act
        accountBalanceReadModel.project(events);

        // Assert
        AccountBalanceView accountBalance = accountBalanceReadModel.getAccountBalance(accountId.id());
        assertAll(
                () -> assertThat(accountBalance.accountId()).isEqualTo(accountId.id()),
                () -> assertThat(accountBalance.currency()).isEqualTo("USD"),
                () -> assertThat(accountBalance.balance()).isEqualByComparingTo(BigDecimal.ZERO),
                () -> assertThat(accountBalance.reservedFunds()).isEqualByComparingTo(BigDecimal.ZERO),
                () -> assertThat(accountBalance.status()).isEqualTo("CLOSED"),
                () -> assertThat(accountBalance.version()).isEqualTo(6L)
        );
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
        dslContext.insertInto(ACCOUNT_BALANCE_VIEW)
                .set(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID, accountId)
                .set(ACCOUNT_BALANCE_VIEW.CURRENCY, currency)
                .set(ACCOUNT_BALANCE_VIEW.BALANCE, balance)
                .set(ACCOUNT_BALANCE_VIEW.RESERVED, reserved)
                .set(ACCOUNT_BALANCE_VIEW.STATUS, status)
                .set(ACCOUNT_BALANCE_VIEW.VERSION, version)
                .execute();

        FundsCredited fundsCredited = new FundsCredited(
                AccountId.of(accountId),
                Instant.now(),
                version-1,
                Money.of(BigDecimal.valueOf(500L), SupportedCurrency.USD)
        );

        List<AccountEvent> events = List.of(fundsCredited);

        // Act ... Assert
        assertThatExceptionOfType(OptimisticLockingFailureException.class)
                .isThrownBy(() -> accountBalanceReadModel.project(events));
    }

}