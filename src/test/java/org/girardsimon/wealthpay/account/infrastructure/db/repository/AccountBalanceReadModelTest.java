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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCancelled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.EventId;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@JooqTest
@Import({AccountBalanceReadModel.class, AccountBalanceViewEntryToDomainMapper.class})
class AccountBalanceReadModelTest extends AbstractContainerTest {

  @Autowired private DSLContext dslContext;
  @Autowired private PlatformTransactionManager transactionManager;

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
    AccountEventMeta meta1 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened =
        new AccountOpened(
            meta1,
            SupportedCurrency.USD,
            Money.of(BigDecimal.valueOf(1000L), SupportedCurrency.USD));
    AccountEventMeta meta2 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsCredited fundsCredited =
        new FundsCredited(
            meta2,
            TransactionId.newId(),
            Money.of(BigDecimal.valueOf(500L), SupportedCurrency.USD));
    ReservationId reservationId = ReservationId.newId();
    Money moneyReserved = Money.of(BigDecimal.valueOf(200L), SupportedCurrency.USD);
    AccountEventMeta meta3 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 3L);
    FundsReserved fundsReserved = new FundsReserved(meta3, reservationId, moneyReserved);
    AccountEventMeta meta4 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 4L);
    ReservationCancelled reservationCancelled =
        new ReservationCancelled(meta4, reservationId, moneyReserved);
    AccountEventMeta meta5 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 5L);
    FundsDebited fundsDebited =
        new FundsDebited(
            meta5,
            TransactionId.newId(),
            Money.of(BigDecimal.valueOf(1500L), SupportedCurrency.USD));
    AccountEventMeta meta6 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 6L);
    AccountClosed accountClosed = new AccountClosed(meta6);
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
    AccountEventMeta meta1 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened =
        new AccountOpened(
            meta1,
            SupportedCurrency.USD,
            Money.of(BigDecimal.valueOf(1000L), SupportedCurrency.USD));
    ReservationId reservationId = ReservationId.newId();
    Money moneyReserved = Money.of(BigDecimal.valueOf(200L), SupportedCurrency.USD);
    AccountEventMeta meta2 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsReserved fundsReserved = new FundsReserved(meta2, reservationId, moneyReserved);
    AccountEventMeta meta3 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 3L);
    ReservationCaptured reservationCaptured =
        new ReservationCaptured(meta3, reservationId, moneyReserved);

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
  void
      project_should_throw_illegal_state_exception_when_expected_version_is_inconsistent_to_read_model() {
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
    AccountEventMeta meta =
        AccountEventMeta.of(EventId.newId(), AccountId.of(accountId), Instant.now(), version + 10L);
    FundsCredited fundsCredited =
        new FundsCredited(
            meta, TransactionId.newId(), Money.of(BigDecimal.valueOf(500L), SupportedCurrency.USD));

    List<AccountEvent> events = List.of(fundsCredited);

    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> accountBalanceReadModel.project(events));
  }

  @Test
  void project_should_be_idempotent() throws Exception {
    // Arrange
    AccountId accountId = AccountId.newId();
    long initialVersion = 55L;
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    // REQUIRES_NEW forces the actual commit (otherwise @JooqTest rolls back everything)
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    txTemplate.execute(
        _ -> {
          String currency = "USD";
          BigDecimal balance = BigDecimal.valueOf(1000L);
          BigDecimal reserved = BigDecimal.ZERO;
          String status = "OPENED";
          dslContext
              .insertInto(ACCOUNT_BALANCE_VIEW)
              .set(ACCOUNT_BALANCE_VIEW.ACCOUNT_ID, accountId.id())
              .set(ACCOUNT_BALANCE_VIEW.CURRENCY, currency)
              .set(ACCOUNT_BALANCE_VIEW.BALANCE, balance)
              .set(ACCOUNT_BALANCE_VIEW.RESERVED, reserved)
              .set(ACCOUNT_BALANCE_VIEW.STATUS, status)
              .set(ACCOUNT_BALANCE_VIEW.VERSION, initialVersion)
              .execute();
          return null;
        });
    int threads = 2;
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();
    try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        Runnable runnable =
            () -> {
              ready.countDown(); // Signal "I'm ready"
              try {
                go.await();
                AccountEventMeta metaCredited =
                    AccountEventMeta.of(
                        EventId.newId(), accountId, Instant.now(), initialVersion + 1L);
                FundsCredited credited =
                    new FundsCredited(
                        metaCredited,
                        TransactionId.newId(),
                        Money.of(BigDecimal.TEN, SupportedCurrency.USD));

                txTemplate.execute(
                    _ -> {
                      accountBalanceReadModel.project(List.of(credited));
                      return null;
                    });
                successes.incrementAndGet();
              } catch (InterruptedException _) {
                failures.incrementAndGet();
              }
            };
        executor.submit(runnable);
      }

      // Act
      ready.await(); // Wait until all threads are ready
      go.countDown();
      executor.shutdown();
      boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
      assertThat(terminated).isTrue();
    }

    // Assert
    AccountBalanceView accountBalance = accountBalanceReadModel.getAccountBalance(accountId);
    assertAll(
        () -> assertThat(successes.get()).isEqualTo(2),
        () -> assertThat(failures.get()).isZero(),
        () -> assertThat(accountBalance.version()).isEqualTo(initialVersion + 1L));
  }
}
