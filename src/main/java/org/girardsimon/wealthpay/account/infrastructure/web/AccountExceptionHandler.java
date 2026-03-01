package org.girardsimon.wealthpay.account.infrastructure.web;

import org.girardsimon.wealthpay.account.domain.exception.AccountBalanceNotFoundException;
import org.girardsimon.wealthpay.account.domain.exception.AccountCurrencyMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountHistoryNotFoundException;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.exception.AccountNotEmptyException;
import org.girardsimon.wealthpay.account.domain.exception.AmountMustBePositiveException;
import org.girardsimon.wealthpay.account.domain.exception.InsufficientFundsException;
import org.girardsimon.wealthpay.account.domain.exception.InvalidAccountEventStreamException;
import org.girardsimon.wealthpay.account.domain.exception.InvalidInitialBalanceException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCanceledException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCapturedException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationNotFoundException;
import org.girardsimon.wealthpay.account.domain.exception.TransactionIdConflictException;
import org.girardsimon.wealthpay.account.domain.exception.UnsupportedCurrencyException;
import org.girardsimon.wealthpay.shared.api.generated.model.ApiErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AccountExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(AccountExceptionHandler.class);

  @ExceptionHandler({AccountIdMismatchException.class})
  public ResponseEntity<ApiErrorDto> handleBadRequestException(Exception e) {
    log.warn("Bad Request exception: ", e);
    ApiErrorDto apiErrorDto =
        new ApiErrorDto().status(HttpStatus.BAD_REQUEST.value()).message(e.getMessage());

    return ResponseEntity.badRequest().body(apiErrorDto);
  }

  @ExceptionHandler({
    ReservationNotFoundException.class,
    AccountHistoryNotFoundException.class,
    AccountBalanceNotFoundException.class
  })
  public ResponseEntity<ApiErrorDto> handleNotFoundException(Exception e) {
    log.warn("Not Found exception: ", e);
    ApiErrorDto apiErrorDto =
        new ApiErrorDto().status(HttpStatus.NOT_FOUND.value()).message(e.getMessage());

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiErrorDto);
  }

  @ExceptionHandler({
    AccountInactiveException.class,
    ReservationAlreadyCanceledException.class,
    ReservationAlreadyCapturedException.class,
    TransactionIdConflictException.class
  })
  public ResponseEntity<ApiErrorDto> handleConflictException(Exception e) {
    log.warn("Conflict exception: ", e);
    ApiErrorDto apiErrorDto =
        new ApiErrorDto().status(HttpStatus.CONFLICT.value()).message(e.getMessage());

    return ResponseEntity.status(HttpStatus.CONFLICT).body(apiErrorDto);
  }

  @ExceptionHandler({
    InvalidInitialBalanceException.class,
    AccountCurrencyMismatchException.class,
    AmountMustBePositiveException.class,
    InsufficientFundsException.class,
    AccountNotEmptyException.class,
    UnsupportedCurrencyException.class
  })
  public ResponseEntity<ApiErrorDto> handleUnprocessableEntityException(Exception e) {
    log.warn("Unprocessable Entity exception: ", e);
    ApiErrorDto apiErrorDto =
        new ApiErrorDto().status(HttpStatus.UNPROCESSABLE_CONTENT.value()).message(e.getMessage());

    return ResponseEntity.unprocessableContent().body(apiErrorDto);
  }

  @ExceptionHandler({InvalidAccountEventStreamException.class})
  public ResponseEntity<ApiErrorDto> handleInternalServerErrorException(Exception e) {
    log.error("Internal Server Error exception: ", e);
    ApiErrorDto apiErrorDto =
        new ApiErrorDto().status(HttpStatus.INTERNAL_SERVER_ERROR.value()).message(e.getMessage());

    return ResponseEntity.internalServerError().body(apiErrorDto);
  }
}
