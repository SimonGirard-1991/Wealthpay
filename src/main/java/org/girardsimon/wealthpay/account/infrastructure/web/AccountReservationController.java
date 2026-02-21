package org.girardsimon.wealthpay.account.infrastructure.web;

import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.AccountReservationApi;
import org.girardsimon.wealthpay.account.api.generated.model.CancelReservationResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.CaptureReservationResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsResponseDto;
import org.girardsimon.wealthpay.account.application.AccountApplicationService;
import org.girardsimon.wealthpay.account.application.response.CancelReservationResponse;
import org.girardsimon.wealthpay.account.application.response.CaptureReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsResponse;
import org.girardsimon.wealthpay.account.domain.command.CancelReservation;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.command.ReserveFunds;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.CancelReservationDtoToDomainMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.CancelReservationResponseToDtoMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.CaptureReservationDtoToDomainMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.CaptureReservationResponseToDtoMapper;
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
  private final CaptureReservationResponseToDtoMapper captureReservationResponseToDtoMapper;
  private final CancelReservationDtoToDomainMapper cancelReservationDtoToDomainMapper;
  private final CancelReservationResponseToDtoMapper cancelReservationResponseToDtoMapper;

  public AccountReservationController(
      AccountApplicationService accountApplicationService,
      ReserveFundsDtoToDomainMapper reserveFundsDtoToDomainMapper,
      ReserveFundsResponseToDtoMapper reserveFundsResponseToDtoMapper,
      CaptureReservationDtoToDomainMapper captureReservationDtoToDomainMapper,
      CaptureReservationResponseToDtoMapper captureReservationResponseToDtoMapper,
      CancelReservationDtoToDomainMapper cancelReservationDtoToDomainMapper,
      CancelReservationResponseToDtoMapper cancelReservationResponseToDtoMapper) {
    this.accountApplicationService = accountApplicationService;
    this.reserveFundsDtoToDomainMapper = reserveFundsDtoToDomainMapper;
    this.reserveFundsResponseToDtoMapper = reserveFundsResponseToDtoMapper;
    this.captureReservationDtoToDomainMapper = captureReservationDtoToDomainMapper;
    this.captureReservationResponseToDtoMapper = captureReservationResponseToDtoMapper;
    this.cancelReservationDtoToDomainMapper = cancelReservationDtoToDomainMapper;
    this.cancelReservationResponseToDtoMapper = cancelReservationResponseToDtoMapper;
  }

  @Override
  public ResponseEntity<CancelReservationResponseDto> cancelReservation(
      UUID id, UUID reservationId) {
    CancelReservation cancelReservation =
        cancelReservationDtoToDomainMapper.apply(id, reservationId);
    CancelReservationResponse cancelReservationResponse =
        accountApplicationService.cancelReservation(cancelReservation);
    return ResponseEntity.ok(cancelReservationResponseToDtoMapper.apply(cancelReservationResponse));
  }

  @Override
  public ResponseEntity<CaptureReservationResponseDto> captureReservation(
      UUID id, UUID reservationId) {
    CaptureReservation captureReservation =
        captureReservationDtoToDomainMapper.apply(id, reservationId);
    CaptureReservationResponse captureReservationResponse =
        accountApplicationService.captureReservation(captureReservation);
    return ResponseEntity.ok(
        captureReservationResponseToDtoMapper.apply(captureReservationResponse));
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
