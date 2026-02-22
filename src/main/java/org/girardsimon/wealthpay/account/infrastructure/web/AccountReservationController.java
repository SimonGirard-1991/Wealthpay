package org.girardsimon.wealthpay.account.infrastructure.web;

import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.AccountReservationApi;
import org.girardsimon.wealthpay.account.api.generated.model.ReservationResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsResponseDto;
import org.girardsimon.wealthpay.account.application.AccountApplicationService;
import org.girardsimon.wealthpay.account.application.response.ReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsResponse;
import org.girardsimon.wealthpay.account.domain.command.CancelReservation;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.CancelReservationDtoToDomainMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.CaptureReservationDtoToDomainMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.ReservationResponseToDtoMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.ReserveFundsDtoToDomainMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.ReserveFundsResponseToDtoMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountReservationController implements AccountReservationApi {

  private final AccountApplicationService accountApplicationService;

  private final ReserveFundsDtoToDomainMapper reserveFundsDtoToDomainMapper;
  private final ReserveFundsResponseToDtoMapper reserveFundsResponseToDtoMapper;
  private final CaptureReservationDtoToDomainMapper captureReservationDtoToDomainMapper;
  private final CancelReservationDtoToDomainMapper cancelReservationDtoToDomainMapper;
  private final ReservationResponseToDtoMapper reservationResponseToDtoMapper;

  public AccountReservationController(
      AccountApplicationService accountApplicationService,
      ReserveFundsDtoToDomainMapper reserveFundsDtoToDomainMapper,
      ReserveFundsResponseToDtoMapper reserveFundsResponseToDtoMapper,
      CaptureReservationDtoToDomainMapper captureReservationDtoToDomainMapper,
      CancelReservationDtoToDomainMapper cancelReservationDtoToDomainMapper,
      ReservationResponseToDtoMapper reservationResponseToDtoMapper) {
    this.accountApplicationService = accountApplicationService;
    this.reserveFundsDtoToDomainMapper = reserveFundsDtoToDomainMapper;
    this.reserveFundsResponseToDtoMapper = reserveFundsResponseToDtoMapper;
    this.captureReservationDtoToDomainMapper = captureReservationDtoToDomainMapper;
    this.cancelReservationDtoToDomainMapper = cancelReservationDtoToDomainMapper;
    this.reservationResponseToDtoMapper = reservationResponseToDtoMapper;
  }

  @Override
  public ResponseEntity<ReservationResponseDto> cancelReservation(UUID id, UUID reservationId) {
    CancelReservation cancelReservation =
        cancelReservationDtoToDomainMapper.apply(id, reservationId);
    ReservationResponse reservationResponse =
        accountApplicationService.cancelReservation(cancelReservation);
    return ResponseEntity.ok(reservationResponseToDtoMapper.apply(reservationResponse));
  }

  @Override
  public ResponseEntity<ReservationResponseDto> captureReservation(UUID id, UUID reservationId) {
    CaptureReservation captureReservation =
        captureReservationDtoToDomainMapper.apply(id, reservationId);
    ReservationResponse captureReservationResponse =
        accountApplicationService.captureReservation(captureReservation);
    return ResponseEntity.ok(reservationResponseToDtoMapper.apply(captureReservationResponse));
  }

  @Override
  public ResponseEntity<ReserveFundsResponseDto> reserveFunds(
      UUID id, UUID transactionId, ReserveFundsRequestDto reserveFundsRequestDto) {
    ReserveFunds reserveFunds =
        reserveFundsDtoToDomainMapper.apply(id, transactionId, reserveFundsRequestDto);
    ReserveFundsResponse reserveFundsResponse =
        accountApplicationService.reserveFunds(reserveFunds);
    return ResponseEntity.ok(reserveFundsResponseToDtoMapper.apply(reserveFundsResponse));
  }
}
