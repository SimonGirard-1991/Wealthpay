package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.util.Optional;
import org.girardsimon.wealthpay.account.api.generated.model.ReservationResultDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.application.response.ReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReservationResult;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.junit.jupiter.api.Test;

class ReservationResponseToDtoMapperTest {

  ReservationResponseToDtoMapper mapper = new ReservationResponseToDtoMapper();

  @Test
  void map_cancel_reservation_response_to_dto() {
    // Arrange
    AccountId accountId = AccountId.newId();
    ReservationId reservationId = ReservationId.newId();
    Money money = Money.of(BigDecimal.valueOf(50.25), SupportedCurrency.USD);
    ReservationResponse reservationResponse =
        new ReservationResponse(
            accountId, reservationId, Optional.of(money), ReservationResult.CANCELED);

    // Act
    var cancelReservationResponseDto = mapper.apply(reservationResponse);

    // Assert
    assertAll(
        () -> assertThat(cancelReservationResponseDto.getAccountId()).isEqualTo(accountId.id()),
        () ->
            assertThat(cancelReservationResponseDto.getReservationId())
                .isEqualTo(reservationId.id()),
        () ->
            assertThat(cancelReservationResponseDto.getStatus())
                .isEqualTo(ReservationResultDto.CANCELED),
        () -> assertThat(cancelReservationResponseDto.getAmount()).isEqualByComparingTo("50.25"),
        () ->
            assertThat(cancelReservationResponseDto.getCurrency())
                .isEqualTo(SupportedCurrencyDto.USD));
  }

  @Test
  void map_cancel_reservation_response_without_money_to_dto() {
    // Arrange
    AccountId accountId = AccountId.newId();
    ReservationId reservationId = ReservationId.newId();
    ReservationResponse reservationResponse =
        new ReservationResponse(
            accountId, reservationId, Optional.empty(), ReservationResult.NO_EFFECT);

    // Act
    var cancelReservationResponseDto = mapper.apply(reservationResponse);

    // Assert
    assertAll(
        () -> assertThat(cancelReservationResponseDto.getAccountId()).isEqualTo(accountId.id()),
        () ->
            assertThat(cancelReservationResponseDto.getReservationId())
                .isEqualTo(reservationId.id()),
        () ->
            assertThat(cancelReservationResponseDto.getStatus())
                .isEqualTo(ReservationResultDto.NO_EFFECT),
        () -> assertThat(cancelReservationResponseDto.getAmount()).isNull(),
        () -> assertThat(cancelReservationResponseDto.getCurrency()).isNull());
  }
}
