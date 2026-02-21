package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.util.Optional;
import org.girardsimon.wealthpay.account.api.generated.model.ReservationCanceledStatusDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.application.response.CancelReservationResponse;
import org.girardsimon.wealthpay.account.application.response.CancelReservationStatus;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.junit.jupiter.api.Test;

class CancelReservationResponseToDtoMapperTest {

  CancelReservationResponseToDtoMapper mapper = new CancelReservationResponseToDtoMapper();

  @Test
  void map_cancel_reservation_response_to_dto() {
    // Arrange
    AccountId accountId = AccountId.newId();
    ReservationId reservationId = ReservationId.newId();
    Money money = Money.of(BigDecimal.valueOf(50.25), SupportedCurrency.USD);
    CancelReservationResponse cancelReservationResponse =
        new CancelReservationResponse(
            accountId, reservationId, Optional.of(money), CancelReservationStatus.CANCELED);

    // Act
    var cancelReservationResponseDto = mapper.apply(cancelReservationResponse);

    // Assert
    assertAll(
        () -> assertThat(cancelReservationResponseDto.getAccountId()).isEqualTo(accountId.id()),
        () ->
            assertThat(cancelReservationResponseDto.getReservationId())
                .isEqualTo(reservationId.id()),
        () ->
            assertThat(cancelReservationResponseDto.getStatus())
                .isEqualTo(ReservationCanceledStatusDto.CANCELED),
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
    CancelReservationResponse cancelReservationResponse =
        new CancelReservationResponse(
            accountId, reservationId, Optional.empty(), CancelReservationStatus.NO_EFFECT);

    // Act
    var cancelReservationResponseDto = mapper.apply(cancelReservationResponse);

    // Assert
    assertAll(
        () -> assertThat(cancelReservationResponseDto.getAccountId()).isEqualTo(accountId.id()),
        () ->
            assertThat(cancelReservationResponseDto.getReservationId())
                .isEqualTo(reservationId.id()),
        () ->
            assertThat(cancelReservationResponseDto.getStatus())
                .isEqualTo(ReservationCanceledStatusDto.NO_EFFECT),
        () -> assertThat(cancelReservationResponseDto.getAmount()).isNull(),
        () -> assertThat(cancelReservationResponseDto.getCurrency()).isNull());
  }
}
