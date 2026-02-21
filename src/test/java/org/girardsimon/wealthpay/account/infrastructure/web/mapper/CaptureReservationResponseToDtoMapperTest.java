package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.util.Optional;
import org.girardsimon.wealthpay.account.api.generated.model.ReservationCaptureStatusDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.application.response.CaptureReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReservationCaptureStatus;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.junit.jupiter.api.Test;

class CaptureReservationResponseToDtoMapperTest {

  CaptureReservationResponseToDtoMapper mapper = new CaptureReservationResponseToDtoMapper();

  @Test
  void map_capture_reservation_response_to_dto() {
    // Arrange
    AccountId accountId = AccountId.newId();
    ReservationId reservationId = ReservationId.newId();
    Money money = Money.of(BigDecimal.valueOf(50.25), SupportedCurrency.USD);
    CaptureReservationResponse captureReservationResponse =
        new CaptureReservationResponse(
            accountId, reservationId, ReservationCaptureStatus.CAPTURED, Optional.of(money));

    // Act
    var captureReservationResponseDto = mapper.apply(captureReservationResponse);

    // Assert
    assertAll(
        () -> assertThat(captureReservationResponseDto.getAccountId()).isEqualTo(accountId.id()),
        () ->
            assertThat(captureReservationResponseDto.getReservationId())
                .isEqualTo(reservationId.id()),
        () ->
            assertThat(captureReservationResponseDto.getStatus())
                .isEqualTo(ReservationCaptureStatusDto.CAPTURED),
        () -> assertThat(captureReservationResponseDto.getAmount()).isEqualByComparingTo("50.25"),
        () ->
            assertThat(captureReservationResponseDto.getCurrency())
                .isEqualTo(SupportedCurrencyDto.USD));
  }

  @Test
  void map_capture_reservation_response_without_money_to_dto() {
    // Arrange
    AccountId accountId = AccountId.newId();
    ReservationId reservationId = ReservationId.newId();
    CaptureReservationResponse captureReservationResponse =
        new CaptureReservationResponse(
            accountId, reservationId, ReservationCaptureStatus.NO_EFFECT, Optional.empty());

    // Act
    var captureReservationResponseDto = mapper.apply(captureReservationResponse);

    // Assert
    assertAll(
        () -> assertThat(captureReservationResponseDto.getAccountId()).isEqualTo(accountId.id()),
        () ->
            assertThat(captureReservationResponseDto.getReservationId())
                .isEqualTo(reservationId.id()),
        () ->
            assertThat(captureReservationResponseDto.getStatus())
                .isEqualTo(ReservationCaptureStatusDto.NO_EFFECT),
        () -> assertThat(captureReservationResponseDto.getAmount()).isNull(),
        () -> assertThat(captureReservationResponseDto.getCurrency()).isNull());
  }
}
