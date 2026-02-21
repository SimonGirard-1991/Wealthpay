package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.function.Function;
import org.girardsimon.wealthpay.account.api.generated.model.CancelReservationResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReservationCanceledStatusDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.application.response.CancelReservationResponse;
import org.springframework.stereotype.Component;

@Component
public class CancelReservationResponseToDtoMapper
    implements Function<CancelReservationResponse, CancelReservationResponseDto> {

  @Override
  public CancelReservationResponseDto apply(CancelReservationResponse cancelReservationResponse) {
    CancelReservationResponseDto responseDto =
        new CancelReservationResponseDto()
            .accountId(cancelReservationResponse.accountId().id())
            .reservationId(cancelReservationResponse.reservationId().id())
            .status(
                ReservationCanceledStatusDto.valueOf(
                    cancelReservationResponse.cancelReservationStatus().name()));

    cancelReservationResponse
        .money()
        .ifPresent(
            money ->
                responseDto
                    .amount(money.amount())
                    .currency(SupportedCurrencyDto.valueOf(money.currency().name())));

    return responseDto;
  }
}
