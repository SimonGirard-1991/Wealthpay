package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.UUID;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.junit.jupiter.api.Test;

class CaptureReservationDtoToDomainMapperTest {

  CaptureReservationDtoToDomainMapper mapper = new CaptureReservationDtoToDomainMapper();

  @Test
  void map_capture_reservation_dto_to_command() {
    // Arrange
    UUID accountId = UUID.randomUUID();
    UUID reservationId = UUID.randomUUID();

    // Act
    CaptureReservation captureReservation = mapper.apply(accountId, reservationId);

    // Assert
    assertAll(
        () -> assertThat(captureReservation.accountId()).isEqualTo(AccountId.of(accountId)),
        () ->
            assertThat(captureReservation.reservationId())
                .isEqualTo(ReservationId.of(reservationId)));
  }
}
