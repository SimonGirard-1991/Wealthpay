package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.function.Function;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsStatusDto;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsResponse;
import org.springframework.stereotype.Component;

@Component
public class ReserveFundsResponseToDtoMapper
    implements Function<ReserveFundsResponse, ReserveFundsResponseDto> {

  @Override
  public ReserveFundsResponseDto apply(ReserveFundsResponse reserveFundsResponse) {
    return new ReserveFundsResponseDto()
        .reservationId(reserveFundsResponse.reservationId().id())
        .status(ReserveFundsStatusDto.valueOf(reserveFundsResponse.reserveFundsStatus().name()));
  }
}
