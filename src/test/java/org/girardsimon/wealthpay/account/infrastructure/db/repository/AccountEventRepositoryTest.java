package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.girardsimon.wealthpay.account.application.AccountEventStore;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountEventSerializer;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.EventStoreEntryToAccountEventMapper;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@JooqTest
@Import({
  AccountEventRepository.class,
  EventStoreEntryToAccountEventMapper.class,
  AccountEventSerializer.class,
  ObjectMapper.class
})
class AccountEventRepositoryTest extends AbstractContainerTest {

  @Autowired private DSLContext dsl;
  @Autowired private AccountEventStore accountEventStore;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void loadEvents_should_return_deserialized_AccountOpened_event() {
    // Arrange
    AccountId accountId = AccountId.newId();
    String payloadJson =
        """
            {
                "currency": "USD",
                "initialBalance": 10,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """;
    dsl.insertInto(table(name("account", "event_store")))
        .columns(field("account_id"), field("version"), field("event_type"), field("payload"))
        .values(accountId.id(), 1L, "AccountOpened", JSONB.valueOf(payloadJson))
        .execute();

    // Act
    List<AccountEvent> events = accountEventStore.loadEvents(accountId);

    // Assert
    assertThat(events).hasSize(1);
    AccountEvent first = events.getFirst();
    assertThat(first).isInstanceOf(AccountOpened.class);
    AccountOpened accountOpened = (AccountOpened) first;
    assertAll(
        () -> assertThat(accountOpened.accountId()).isEqualTo(accountId),
        () ->
            assertThat(accountOpened.occurredAt()).isEqualTo(Instant.parse("2025-11-16T15:00:00Z")),
        () -> assertThat(accountOpened.version()).isEqualTo(1L),
        () -> assertThat(accountOpened.currency()).isEqualTo(SupportedCurrency.USD),
        () ->
            assertThat(accountOpened.initialBalance().amount())
                .isEqualTo(BigDecimal.valueOf(10L).setScale(2, RoundingMode.HALF_EVEN)),
        () ->
            assertThat(accountOpened.initialBalance().currency()).isEqualTo(SupportedCurrency.USD));
  }

  @Test
  void appendEvents_persists_events_nominally() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.TEN, usd);
    Instant occurredAt = Instant.parse("2025-11-16T15:00:00Z");

    AccountOpened opened = new AccountOpened(accountId, occurredAt, 1L, usd, initialBalance);

    // Act
    accountEventStore.appendEvents(accountId, 0L, List.of(opened));

    // Assert
    List<AccountEvent> events = accountEventStore.loadEvents(accountId);
    assertThat(events).hasSize(1);
    AccountEvent first = events.getFirst();
    assertThat(first).isInstanceOf(AccountOpened.class);
    AccountOpened accountOpened = (AccountOpened) first;
    assertAll(
        () -> assertThat(accountOpened.accountId()).isEqualTo(accountId),
        () ->
            assertThat(accountOpened.occurredAt()).isEqualTo(Instant.parse("2025-11-16T15:00:00Z")),
        () -> assertThat(accountOpened.version()).isEqualTo(1L),
        () -> assertThat(accountOpened.currency()).isEqualTo(SupportedCurrency.USD),
        () ->
            assertThat(accountOpened.initialBalance().amount())
                .isEqualTo(BigDecimal.valueOf(10L).setScale(2, RoundingMode.HALF_EVEN)),
        () ->
            assertThat(accountOpened.initialBalance().currency()).isEqualTo(SupportedCurrency.USD));
  }

  @Test
  void appendEvents_throws_OptimisticLockingFailureException_when_expectedVersion_is_outdated() {
    // Arrange
    AccountId accountId = AccountId.newId();
    dsl.insertInto(table(name("account", "event_store")))
        .columns(field("account_id"), field("version"), field("event_type"), field("payload"))
        .values(accountId.id(), 1L, "AccountOpened", JSONB.valueOf("{}"))
        .execute();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.TEN, usd);
    AccountOpened opened =
        new AccountOpened(
            accountId, Instant.parse("2025-11-16T15:00:00Z"), 1L, usd, initialBalance);

    // Act ... Assert
    List<AccountEvent> openedEvents = List.of(opened);
    assertThatExceptionOfType(OptimisticLockingFailureException.class)
        .isThrownBy(() -> accountEventStore.appendEvents(accountId, 0L, openedEvents));
  }

  @Test
  void
      appendEvents_throws_OptimisticLockingFailureException_when_expectedVersion_ahead_of_actual() {
    // Arrange
    AccountId accountId = AccountId.newId();
    dsl.insertInto(table(name("account", "event_store")))
        .columns(field("account_id"), field("version"), field("event_type"), field("payload"))
        .values(accountId.id(), 1L, "AccountOpened", JSONB.valueOf("{}"))
        .execute();
    FundsCredited credited =
        new FundsCredited(
            TransactionId.newId(),
            accountId,
            Instant.now(),
            6L,
            Money.of(BigDecimal.TEN, SupportedCurrency.USD));

    // Act ... Assert
    List<AccountEvent> creditedEvents = List.of(credited);
    assertThatExceptionOfType(OptimisticLockingFailureException.class)
        .isThrownBy(() -> accountEventStore.appendEvents(accountId, 5L, creditedEvents));
  }

  @Test
  void appendEvents_throws_IllegalStateException_when_event_versions_have_gap() {
    // Arrange
    AccountId accountId = AccountId.newId();
    AccountOpened opened =
        new AccountOpened(
            accountId,
            Instant.now(),
            3L,
            SupportedCurrency.USD,
            Money.of(BigDecimal.TEN, SupportedCurrency.USD));

    // Act ... Assert
    List<AccountEvent> accountEvents = List.of(opened);
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> accountEventStore.appendEvents(accountId, 0L, accountEvents));
  }

  @Test
  void appendEvents_concurrent_modification_one_wins() throws Exception {
    // Arrange
    // Setup: create an account with a committed initial event
    AccountId accountId = AccountId.newId();
    AccountOpened opened =
        new AccountOpened(
            accountId,
            Instant.now(),
            1L,
            SupportedCurrency.USD,
            Money.of(BigDecimal.TEN, SupportedCurrency.USD));
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    // REQUIRES_NEW forces the actual commit (otherwise @JooqTest rolls back everything)
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    txTemplate.execute(
        _ -> {
          accountEventStore.appendEvents(accountId, 0L, List.of(opened));
          return null;
        });
    // Prepare N threads that will all attempt to write version 2
    int threads = 10;
    CountDownLatch ready = new CountDownLatch(threads); // All threads ready
    CountDownLatch go = new CountDownLatch(1); // Start signal
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();
    try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        Runnable runnable =
            () -> {
              ready.countDown(); // Signal "I'm ready"
              try {
                go.await(); // Wait for a common start signal
                FundsCredited credited =
                    new FundsCredited(
                        TransactionId.newId(),
                        accountId,
                        Instant.now(),
                        2L,
                        Money.of(BigDecimal.TEN, SupportedCurrency.USD));

                txTemplate.execute(
                    _ -> {
                      accountEventStore.appendEvents(accountId, 1L, List.of(credited));
                      return null;
                    });
                successes.incrementAndGet();
              } catch (OptimisticLockingFailureException | InterruptedException _) {
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
    assertAll(
        () -> assertThat(successes.get()).isEqualTo(1), // Only one thread succeeded
        () -> assertThat(failures.get()).isEqualTo(threads - 1));
  }
}
