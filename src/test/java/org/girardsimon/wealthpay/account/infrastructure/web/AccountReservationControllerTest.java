package org.girardsimon.wealthpay.account.infrastructure.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.ReservationResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReservationResultDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.ReserveFundsResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
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
import org.girardsimon.wealthpay.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AccountReservationController.class)
@Import(GlobalExceptionHandler.class)
class AccountReservationControllerTest {

  @MockitoBean AccountApplicationService accountApplicationService;

  @MockitoBean ReserveFundsDtoToDomainMapper reserveFundsDtoToDomainMapper;

  @MockitoBean ReserveFundsResponseToDtoMapper reserveFundsResponseToDtoMapper;

  @MockitoBean CaptureReservationDtoToDomainMapper captureReservationDtoToDomainMapper;

  @MockitoBean CancelReservationDtoToDomainMapper cancelReservationDtoToDomainMapper;

  @MockitoBean ReservationResponseToDtoMapper reservationResponseToDtoMapper;

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @Test
  void reserveFunds_should_return_200_with_reservation_status() throws Exception {
    // Arrange
    UUID accountId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();
    ReserveFundsRequestDto reserveFundsRequestDto =
        new ReserveFundsRequestDto()
            .amount(BigDecimal.valueOf(100.50))
            .currency(SupportedCurrencyDto.USD);
    ReserveFunds reserveFunds = mock(ReserveFunds.class);
    ReserveFundsResponse reserveFundsResponse = mock(ReserveFundsResponse.class);
    when(reserveFundsDtoToDomainMapper.apply(accountId, transactionId, reserveFundsRequestDto))
        .thenReturn(reserveFunds);
    when(accountApplicationService.reserveFunds(reserveFunds)).thenReturn(reserveFundsResponse);
    UUID reservationId = UUID.randomUUID();
    when(reserveFundsResponseToDtoMapper.apply(reserveFundsResponse))
        .thenReturn(
            new ReserveFundsResponseDto()
                .reservationId(reservationId)
                .status(ReservationResultDto.RESERVED));

    // Act ... Assert
    mockMvc
        .perform(
            post("/accounts/{id}/reservations", accountId)
                .header("transaction-id", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reserveFundsRequestDto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESERVED"))
        .andExpect(jsonPath("$.reservationId").value(reservationId.toString()));
  }

  @Test
  void captureReservation_should_return_200_with_capture_status() throws Exception {
    // Arrange
    UUID accountId = UUID.randomUUID();
    UUID reservationId = UUID.randomUUID();
    CaptureReservation captureReservation = mock(CaptureReservation.class);
    ReservationResponse captureReservationResponse = mock(ReservationResponse.class);
    when(captureReservationDtoToDomainMapper.apply(accountId, reservationId))
        .thenReturn(captureReservation);
    when(accountApplicationService.captureReservation(captureReservation))
        .thenReturn(captureReservationResponse);
    when(reservationResponseToDtoMapper.apply(captureReservationResponse))
        .thenReturn(
            new ReservationResponseDto()
                .accountId(accountId)
                .reservationId(reservationId)
                .status(ReservationResultDto.CAPTURED)
                .amount(BigDecimal.valueOf(25.10))
                .currency(SupportedCurrencyDto.USD));

    // Act ... Assert
    mockMvc
        .perform(
            post("/accounts/{id}/reservations/{reservationId}/capture", accountId, reservationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(accountId.toString()))
        .andExpect(jsonPath("$.reservationId").value(reservationId.toString()))
        .andExpect(jsonPath("$.status").value("CAPTURED"))
        .andExpect(jsonPath("$.amount").value(25.10))
        .andExpect(jsonPath("$.currency").value("USD"));
  }

  @Test
  void cancelReservation_should_return_200_with_cancel_status() throws Exception {
    // Arrange
    UUID accountId = UUID.randomUUID();
    UUID reservationId = UUID.randomUUID();
    CancelReservation cancelReservation = mock(CancelReservation.class);
    ReservationResponse reservationResponse = mock(ReservationResponse.class);
    when(cancelReservationDtoToDomainMapper.apply(accountId, reservationId))
        .thenReturn(cancelReservation);
    when(accountApplicationService.cancelReservation(cancelReservation))
        .thenReturn(reservationResponse);
    when(reservationResponseToDtoMapper.apply(reservationResponse))
        .thenReturn(
            new ReservationResponseDto()
                .accountId(accountId)
                .reservationId(reservationId)
                .status(ReservationResultDto.CANCELED)
                .amount(BigDecimal.valueOf(25.10))
                .currency(SupportedCurrencyDto.USD));

    // Act ... Assert
    mockMvc
        .perform(
            post("/accounts/{id}/reservations/{reservationId}/cancel", accountId, reservationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(accountId.toString()))
        .andExpect(jsonPath("$.reservationId").value(reservationId.toString()))
        .andExpect(jsonPath("$.status").value("CANCELED"))
        .andExpect(jsonPath("$.amount").value(25.10))
        .andExpect(jsonPath("$.currency").value("USD"));
  }
}
