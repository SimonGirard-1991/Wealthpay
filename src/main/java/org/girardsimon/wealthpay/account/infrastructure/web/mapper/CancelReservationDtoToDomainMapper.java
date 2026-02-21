package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.UUID;
import org.girardsimon.wealthpay.account.domain.command.CancelReservation;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.springframework.stereotype.Component;

@Component
public class CancelReservationDtoToDomainMapper {

  public CancelReservation apply(UUID accountId, UUID reservationId) {
    return new CancelReservation(AccountId.of(accountId), ReservationId.of(reservationId));
  }
}
