package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.UUID;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.springframework.stereotype.Component;

@Component
public class CaptureReservationDtoToDomainMapper {

  public CaptureReservation apply(UUID accountId, UUID reservationId) {
    return new CaptureReservation(AccountId.of(accountId), ReservationId.of(reservationId));
  }
}
