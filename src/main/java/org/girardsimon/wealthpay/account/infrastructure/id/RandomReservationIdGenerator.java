package org.girardsimon.wealthpay.account.infrastructure.id;

import com.github.f4b6a3.uuid.UuidCreator;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.ReservationIdGenerator;
import org.springframework.stereotype.Component;

/**
 * Mints {@link ReservationId}s as UUIDv7 (RFC 9562) for B-tree insert locality on the idempotency
 * PK.
 */
@Component
public class RandomReservationIdGenerator implements ReservationIdGenerator {
  @Override
  public ReservationId newId() {
    return ReservationId.of(UuidCreator.getTimeOrderedEpoch());
  }
}
