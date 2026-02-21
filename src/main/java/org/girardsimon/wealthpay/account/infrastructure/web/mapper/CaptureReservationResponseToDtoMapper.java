package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.function.Function;
import org.girardsimon.wealthpay.account.api.generated.model.CaptureReservationResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReservationCaptureStatusDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.application.response.CaptureReservationResponse;
import org.springframework.stereotype.Component;

@Component
public class CaptureReservationResponseToDtoMapper
    implements Function<CaptureReservationResponse, CaptureReservationResponseDto> {

  @Override
  public CaptureReservationResponseDto apply(
      CaptureReservationResponse captureReservationResponse) {
    CaptureReservationResponseDto responseDto =
        new CaptureReservationResponseDto()
            .accountId(captureReservationResponse.accountId().id())
            .reservationId(captureReservationResponse.reservationId().id())
            .status(
                ReservationCaptureStatusDto.valueOf(
                    captureReservationResponse.reservationCaptureStatus().name()));

    captureReservationResponse
        .money()
        .ifPresent(
            money ->
                responseDto
                    .amount(money.amount())
                    .currency(SupportedCurrencyDto.valueOf(money.currency().name())));

    return responseDto;
  }
}
