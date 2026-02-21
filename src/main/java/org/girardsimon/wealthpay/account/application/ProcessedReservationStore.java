package org.girardsimon.wealthpay.account.application;

import java.time.Instant;
import java.util.Optional;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.ReservationPhase;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;

public interface ProcessedReservationStore {

  Optional<ReservationPhase> lookup(AccountId accountId, ReservationId reservationId);

  ReservationId lookup(AccountId accountId, TransactionId transactionId);

  void register(
      AccountId accountId,
      TransactionId transactionId,
      ReservationId reservationId,
      ReservationPhase reservationPhase,
      Instant occurredAt);

  void updatePhase(
      AccountId accountId,
      ReservationId reservationId,
      ReservationPhase reservationPhase,
      Instant occurredAt);
}
