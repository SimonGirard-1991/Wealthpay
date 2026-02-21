package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsStatusDto;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsResponse;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsStatus;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.junit.jupiter.api.Test;

class ReserveFundsResponseToDtoMapperTest {

  ReserveFundsResponseToDtoMapper mapper = new ReserveFundsResponseToDtoMapper();

  @Test
  void map_reserve_funds_response_to_dto() {
    // Arrange
    ReservationId reservationId = ReservationId.newId();
    ReserveFundsResponse reserveFundsResponse =
        new ReserveFundsResponse(reservationId, ReserveFundsStatus.RESERVED);

    // Act
    ReserveFundsResponseDto reserveFundsResponseDto = mapper.apply(reserveFundsResponse);

    // Assert
    assertAll(
        () -> assertThat(reserveFundsResponseDto.getReservationId()).isEqualTo(reservationId.id()),
        () ->
            assertThat(reserveFundsResponseDto.getStatus())
                .isEqualTo(ReserveFundsStatusDto.RESERVED));
  }
}
