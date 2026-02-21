package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.girardsimon.wealthpay.account.jooq.tables.ProcessedReservations.PROCESSED_RESERVATIONS;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.girardsimon.wealthpay.account.application.ProcessedReservationStore;
import org.girardsimon.wealthpay.account.domain.exception.ReservationNotFoundException;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.ReservationPhase;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.jooq.tables.records.ProcessedReservationsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedReservationRepository implements ProcessedReservationStore {

  private final DSLContext dslContext;
  private final Clock clock;

  public ProcessedReservationRepository(DSLContext dslContext, Clock clock) {
    this.dslContext = dslContext;
    this.clock = clock;
  }

  @Override
  public Optional<ReservationPhase> lookup(AccountId accountId, ReservationId reservationId) {
    return dslContext
        .select(PROCESSED_RESERVATIONS.PHASE)
        .from(PROCESSED_RESERVATIONS)
        .where(PROCESSED_RESERVATIONS.ACCOUNT_ID.eq(accountId.id()))
        .and(PROCESSED_RESERVATIONS.RESERVATION_ID.eq(reservationId.id()))
        .fetchOptional()
        .map(record1 -> ReservationPhase.valueOf(record1.get(PROCESSED_RESERVATIONS.PHASE)));
  }

  @Override
  public ReservationId lookup(AccountId accountId, TransactionId transactionId) {
    return dslContext
        .select(PROCESSED_RESERVATIONS.RESERVATION_ID)
        .from(PROCESSED_RESERVATIONS)
        .where(PROCESSED_RESERVATIONS.ACCOUNT_ID.eq(accountId.id()))
        .and(PROCESSED_RESERVATIONS.TRANSACTION_ID.eq(transactionId.id()))
        .fetchOptional()
        .map(record1 -> ReservationId.of(record1.get(PROCESSED_RESERVATIONS.RESERVATION_ID)))
        .orElseThrow(() -> new ReservationNotFoundException("Reservation not found"));
  }

  @Override
  public void register(
      AccountId accountId,
      TransactionId transactionId,
      ReservationId reservationId,
      ReservationPhase reservationPhase,
      Instant occurredAt) {
    ProcessedReservationsRecord row = dslContext.newRecord(PROCESSED_RESERVATIONS);
    row.setAccountId(accountId.id());
    row.setReservationId(reservationId.id());
    row.setTransactionId(transactionId.id());
    row.setPhase(reservationPhase.name());
    row.setOccurredAt(OffsetDateTime.ofInstant(occurredAt, clock.getZone()));

    dslContext
        .insertInto(PROCESSED_RESERVATIONS)
        .set(row)
        .onConflict(PROCESSED_RESERVATIONS.ACCOUNT_ID, PROCESSED_RESERVATIONS.RESERVATION_ID)
        .doNothing()
        .execute();
  }

  @Override
  public void updatePhase(
      AccountId accountId,
      ReservationId reservationId,
      ReservationPhase reservationPhase,
      Instant occurredAt) {
    dslContext
        .update(PROCESSED_RESERVATIONS)
        .set(PROCESSED_RESERVATIONS.PHASE, reservationPhase.name())
        .set(
            PROCESSED_RESERVATIONS.OCCURRED_AT,
            OffsetDateTime.ofInstant(occurredAt, clock.getZone()))
        .where(PROCESSED_RESERVATIONS.ACCOUNT_ID.eq(accountId.id()))
        .and(PROCESSED_RESERVATIONS.RESERVATION_ID.eq(reservationId.id()))
        .execute();
  }
}
