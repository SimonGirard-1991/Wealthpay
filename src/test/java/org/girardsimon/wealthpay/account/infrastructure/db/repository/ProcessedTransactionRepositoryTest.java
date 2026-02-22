package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.girardsimon.wealthpay.account.application.ProcessedTransactionStore;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.domain.exception.TransactionIdConflictException;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.shared.config.TimeConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@JooqTest
@Import({ProcessedTransactionRepository.class, ObjectMapper.class, TimeConfig.class})
class ProcessedTransactionRepositoryTest extends AbstractContainerTest {

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private ProcessedTransactionStore processedTransactionStore;

  @Test
  void
      register_transaction_should_only_insert_one_transaction_between_many_with_same_transaction_id()
          throws Exception {
    // Arrange
    AccountId accountId = AccountId.newId();
    TransactionId transactionId = TransactionId.newId();
    Instant occurredAt = Instant.now();
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    int threads = 5;
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger numberOfCommited = new AtomicInteger();
    AtomicInteger numberOfNoOp = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();
    try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        Runnable runnable =
            () -> {
              ready.countDown();
              try {
                go.await();

                txTemplate.execute(
                    _ -> {
                      CreditAccount creditAccount =
                          new CreditAccount(
                              transactionId,
                              accountId,
                              Money.of(new BigDecimal("10.00"), SupportedCurrency.USD));
                      TransactionStatus transactionStatus =
                          processedTransactionStore.register(
                              accountId, transactionId, creditAccount.fingerprint(), occurredAt);
                      if (transactionStatus == TransactionStatus.COMMITTED) {
                        numberOfCommited.incrementAndGet();
                      } else {
                        numberOfNoOp.incrementAndGet();
                      }
                      return null;
                    });
              } catch (InterruptedException _) {
                failures.incrementAndGet();
              }
            };
        executor.submit(runnable);
      }

      // Act
      ready.await();
      go.countDown();
      executor.shutdown();
      boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
      assertThat(terminated).isTrue();
    }

    // Assert
    assertAll(
        () -> assertThat(numberOfCommited.get()).isEqualTo(1),
        () -> assertThat(numberOfNoOp.get()).isEqualTo(threads - 1),
        () -> assertThat(failures.get()).isZero());
  }

  @Test
  void register_should_throw_when_transaction_id_exists_with_different_fingerprint() {
    // Arrange
    AccountId accountId = AccountId.newId();
    TransactionId transactionId = TransactionId.newId();
    Instant occurredAt = Instant.now();
    CreditAccount creditAccount =
        new CreditAccount(
            transactionId, accountId, Money.of(new BigDecimal("10.00"), SupportedCurrency.USD));
    DebitAccount debitAccount =
        new DebitAccount(
            transactionId, accountId, Money.of(new BigDecimal("10.00"), SupportedCurrency.USD));

    // Act
    TransactionStatus firstStatus =
        processedTransactionStore.register(
            accountId, transactionId, creditAccount.fingerprint(), occurredAt);

    // Assert
    assertAll(
        () -> assertThat(firstStatus).isEqualTo(TransactionStatus.COMMITTED),
        () ->
            assertThatThrownBy(
                    () ->
                        processedTransactionStore.register(
                            accountId, transactionId, debitAccount.fingerprint(), occurredAt))
                .isInstanceOf(TransactionIdConflictException.class));
  }
}
