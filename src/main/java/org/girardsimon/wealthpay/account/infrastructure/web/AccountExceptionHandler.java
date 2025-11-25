package org.girardsimon.wealthpay.account.infrastructure.web;

import org.girardsimon.wealthpay.account.domain.exception.AccountAlreadyExistsException;
import org.girardsimon.wealthpay.account.domain.exception.AccountBalanceNotFoundException;
import org.girardsimon.wealthpay.account.domain.exception.AccountCurrencyMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountHistoryNotFound;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.exception.AccountNotEmptyException;
import org.girardsimon.wealthpay.account.domain.exception.AmountMustBePositiveException;
import org.girardsimon.wealthpay.account.domain.exception.InsufficientFundsException;
import org.girardsimon.wealthpay.account.domain.exception.InvalidAccountEventStreamException;
import org.girardsimon.wealthpay.account.domain.exception.InvalidInitialBalanceException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyExistsException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationNotFoundException;
import org.girardsimon.wealthpay.account.domain.exception.UnsupportedCurrencyException;
import org.girardsimon.wealthpay.shared.api.generated.model.ApiErrorDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AccountExceptionHandler {

    @ExceptionHandler({
            AccountIdMismatchException.class
    })
    public ResponseEntity<ApiErrorDto> handleBadRequestException(Exception e) {
        ApiErrorDto apiErrorDto = new ApiErrorDto()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(e.getMessage());

        return ResponseEntity
                .badRequest()
                .body(apiErrorDto);
    }

    @ExceptionHandler({
            ReservationNotFoundException.class,
            AccountHistoryNotFound.class,
            AccountBalanceNotFoundException.class
    })
    public ResponseEntity<ApiErrorDto> handleNotFoundException(Exception e) {
        ApiErrorDto apiErrorDto = new ApiErrorDto()
                .status(HttpStatus.NOT_FOUND.value())
                .message(e.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(apiErrorDto);
    }

    @ExceptionHandler({
            AccountInactiveException.class,
            AccountAlreadyExistsException.class
    })
    public ResponseEntity<ApiErrorDto> handleConflictException(Exception e) {
        ApiErrorDto apiErrorDto = new ApiErrorDto()
                .status(HttpStatus.CONFLICT.value())
                .message(e.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(apiErrorDto);
    }

    @ExceptionHandler({
            InvalidInitialBalanceException.class,
            AccountCurrencyMismatchException.class,
            AmountMustBePositiveException.class,
            InsufficientFundsException.class,
            ReservationAlreadyExistsException.class,
            AccountNotEmptyException.class,
            UnsupportedCurrencyException.class
    })
    public ResponseEntity<ApiErrorDto> handleUnprocessableEntityException(Exception e) {
        ApiErrorDto apiErrorDto = new ApiErrorDto()
                .status(HttpStatus.UNPROCESSABLE_CONTENT.value())
                .message(e.getMessage());

        return ResponseEntity
                .unprocessableContent()
                .body(apiErrorDto);
    }

    @ExceptionHandler({
            InvalidAccountEventStreamException.class
    })
    public ResponseEntity<ApiErrorDto> handleInternalServerErrorException(Exception e) {
        ApiErrorDto apiErrorDto = new ApiErrorDto()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(e.getMessage());

        return ResponseEntity
                .internalServerError()
                .body(apiErrorDto);
    }
}
