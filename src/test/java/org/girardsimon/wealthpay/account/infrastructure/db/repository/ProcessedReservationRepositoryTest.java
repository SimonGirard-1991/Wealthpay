package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.girardsimon.wealthpay.account.jooq.tables.ProcessedReservations.PROCESSED_RESERVATIONS;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.girardsimon.wealthpay.account.application.ProcessedReservationStore;
import org.girardsimon.wealthpay.account.domain.exception.ReservationNotFoundException;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.ReservationPhase;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.jooq.tables.records.ProcessedReservationsRecord;
import org.girardsimon.wealthpay.shared.config.TimeConfig;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@JooqTest
@Import({ProcessedReservationRepository.class, TimeConfig.class})
class ProcessedReservationRepositoryTest extends AbstractContainerTest {

  @Autowired private DSLContext dslContext;
  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private ProcessedReservationStore processedReservationStore;

  @Test
  void register_should_persist_reservation_and_support_lookups() {
    // Arrange
    AccountId accountId = AccountId.newId();
    TransactionId transactionId = TransactionId.newId();
    ReservationId reservationId = ReservationId.newId();
    ReservationPhase reservationPhase = ReservationPhase.RESERVED;
    Instant occurredAt = Instant.parse("2026-02-17T01:00:00Z");

    // Act
    processedReservationStore.register(
        accountId, transactionId, reservationId, reservationPhase, occurredAt);

    // Assert
    ProcessedReservationsRecord row =
        dslContext
            .selectFrom(PROCESSED_RESERVATIONS)
            .where(PROCESSED_RESERVATIONS.ACCOUNT_ID.eq(accountId.id()))
            .and(PROCESSED_RESERVATIONS.RESERVATION_ID.eq(reservationId.id()))
            .fetchOne();
    Optional<ReservationPhase> phaseLookup =
        processedReservationStore.lookupPhase(accountId, reservationId);
    ReservationId reservationLookup =
        processedReservationStore.lookupReservation(accountId, transactionId);
    assertAll(
        () -> assertThat(row).isNotNull(),
        () -> assertThat(row.getAccountId()).isEqualTo(accountId.id()),
        () -> assertThat(row.getTransactionId()).isEqualTo(transactionId.id()),
        () -> assertThat(row.getReservationId()).isEqualTo(reservationId.id()),
        () -> assertThat(row.getPhase()).isEqualTo(reservationPhase.name()),
        () ->
            assertThat(row.getOccurredAt()).isEqualTo(OffsetDateTime.parse("2026-02-17T01:00:00Z")),
        () -> assertThat(phaseLookup).contains(ReservationPhase.RESERVED),
        () -> assertThat(reservationLookup).isEqualTo(reservationId));
  }

  @Test
  void lookup_Phase_by_reservation_id_should_return_empty_when_reservation_is_not_found() {
    // Arrange
    AccountId accountId = AccountId.newId();
    ReservationId reservationId = ReservationId.newId();

    // Act
    Optional<ReservationPhase> lookup =
        processedReservationStore.lookupPhase(accountId, reservationId);

    // Assert
    assertThat(lookup).isEmpty();
  }

  @Test
  void lookup_by_transaction_should_throw_reservation_not_found_when_reservation_is_not_found() {
    // Arrange
    AccountId accountId = AccountId.newId();
    TransactionId transactionId = TransactionId.newId();

    // Act ... Assert
    assertThatExceptionOfType(ReservationNotFoundException.class)
        .isThrownBy(() -> processedReservationStore.lookupReservation(accountId, transactionId))
        .withMessage("Reservation not found");
  }

  @Test
  void update_phase_should_update_phase_and_occurred_at() {
    // Arrange
    AccountId accountId = AccountId.newId();
    TransactionId transactionId = TransactionId.newId();
    ReservationId reservationId = ReservationId.newId();
    Instant initialOccurredAt = Instant.parse("2026-02-17T01:00:00Z");
    Instant updatedOccurredAt = Instant.parse("2026-02-17T01:10:00Z");
    processedReservationStore.register(
        accountId, transactionId, reservationId, ReservationPhase.RESERVED, initialOccurredAt);

    // Act
    processedReservationStore.updatePhase(
        accountId, reservationId, ReservationPhase.CAPTURED, updatedOccurredAt);

    // Assert
    ProcessedReservationsRecord row =
        dslContext
            .selectFrom(PROCESSED_RESERVATIONS)
            .where(PROCESSED_RESERVATIONS.ACCOUNT_ID.eq(accountId.id()))
            .and(PROCESSED_RESERVATIONS.RESERVATION_ID.eq(reservationId.id()))
            .fetchOne();
    assertAll(
        () -> assertThat(row).isNotNull(),
        () -> assertThat(row.getPhase()).isEqualTo(ReservationPhase.CAPTURED.name()),
        () ->
            assertThat(row.getOccurredAt())
                .isEqualTo(OffsetDateTime.parse("2026-02-17T01:10:00Z")));
  }

  @Test
  void register_should_be_idempotent_for_same_account_and_reservation() throws Exception {
    // Arrange
    AccountId accountId = AccountId.newId();
    TransactionId transactionId = TransactionId.newId();
    ReservationId reservationId = ReservationId.newId();
    Instant occurredAt = Instant.now();
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    int threads = 5;
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch go = new CountDownLatch(1);
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
                      processedReservationStore.register(
                          accountId,
                          transactionId,
                          reservationId,
                          ReservationPhase.RESERVED,
                          occurredAt);
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
    int records =
        dslContext.fetchCount(
            PROCESSED_RESERVATIONS,
            PROCESSED_RESERVATIONS
                .ACCOUNT_ID
                .eq(accountId.id())
                .and(PROCESSED_RESERVATIONS.RESERVATION_ID.eq(reservationId.id())));
    assertAll(() -> assertThat(records).isEqualTo(1), () -> assertThat(failures.get()).isZero());
  }

  @Test
  void register_should_fail_when_transaction_id_is_already_linked_to_other_reservation() {
    // Arrange
    AccountId accountId = AccountId.newId();
    TransactionId transactionId = TransactionId.newId();
    ReservationId reservationId = ReservationId.newId();
    ReservationId otherReservationId = ReservationId.newId();
    Instant occurredAt = Instant.parse("2026-02-17T01:00:00Z");
    processedReservationStore.register(
        accountId, transactionId, reservationId, ReservationPhase.RESERVED, occurredAt);

    // Act ... Assert
    assertThatExceptionOfType(DataAccessException.class)
        .isThrownBy(
            () ->
                processedReservationStore.register(
                    accountId,
                    transactionId,
                    otherReservationId,
                    ReservationPhase.RESERVED,
                    occurredAt));
  }
}
