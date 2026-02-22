package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.function.Function;
import org.girardsimon.wealthpay.account.api.generated.model.ReservationResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReservationResultDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.application.response.ReservationResponse;
import org.springframework.stereotype.Component;

@Component
public class ReservationResponseToDtoMapper
    implements Function<ReservationResponse, ReservationResponseDto> {

  @Override
  public ReservationResponseDto apply(ReservationResponse reservationResponse) {
    ReservationResponseDto responseDto =
        new ReservationResponseDto()
            .accountId(reservationResponse.accountId().id())
            .reservationId(reservationResponse.reservationId().id())
            .status(ReservationResultDto.valueOf(reservationResponse.reservationResult().name()));

    reservationResponse
        .money()
        .ifPresent(
            money ->
                responseDto
                    .amount(money.amount())
                    .currency(SupportedCurrencyDto.valueOf(money.currency().name())));

    return responseDto;
  }
}
