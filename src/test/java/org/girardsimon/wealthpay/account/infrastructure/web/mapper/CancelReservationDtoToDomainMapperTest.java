package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.UUID;
import org.girardsimon.wealthpay.account.domain.command.CancelReservation;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.junit.jupiter.api.Test;

class CancelReservationDtoToDomainMapperTest {

  CancelReservationDtoToDomainMapper mapper = new CancelReservationDtoToDomainMapper();

  @Test
  void map_cancel_reservation_dto_to_command() {
    // Arrange
    UUID accountId = UUID.randomUUID();
    UUID reservationId = UUID.randomUUID();

    // Act
    CancelReservation cancelReservation = mapper.apply(accountId, reservationId);

    // Assert
    assertAll(
        () -> assertThat(cancelReservation.accountId()).isEqualTo(AccountId.of(accountId)),
        () ->
            assertThat(cancelReservation.reservationId())
                .isEqualTo(ReservationId.of(reservationId)));
  }
}
