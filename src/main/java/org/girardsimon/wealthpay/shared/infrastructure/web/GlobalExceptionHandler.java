package org.girardsimon.wealthpay.shared.infrastructure.web;

import org.girardsimon.wealthpay.shared.api.generated.model.ApiErrorDto;
import org.girardsimon.wealthpay.shared.api.generated.model.FieldErrorDto;
import org.girardsimon.wealthpay.shared.api.generated.model.ValidationErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Optional;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorDto> handleBadRequestException(Exception e) {
        log.warn("Bad Request exception: ", e);
        ApiErrorDto apiErrorDto = new ApiErrorDto()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(e.getMessage());

        return ResponseEntity
                .badRequest()
                .body(apiErrorDto);
    }

    @ExceptionHandler({
            OptimisticLockingFailureException.class
    })
    public ResponseEntity<ApiErrorDto> handleConflictException(Exception e) {
        log.warn("Conflict exception: ", e);
        ApiErrorDto apiErrorDto = new ApiErrorDto()
                .status(HttpStatus.CONFLICT.value())
                .message(e.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(apiErrorDto);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorDto> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("Input validation exception: ", e);
        List<FieldErrorDto> fieldErrors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new FieldErrorDto()
                                .field(fieldError.getField())
                                .message(fieldError.getDefaultMessage())
                                .rejectedValue(Optional.ofNullable(fieldError.getRejectedValue())
                                        .map(Object::toString)
                                        .orElse(null))
                                .code(fieldError.getCode())
                )
                .toList();

        ValidationErrorDto body = new ValidationErrorDto()
                .title("Validation failed")
                .status(HttpStatus.BAD_REQUEST.value())
                .errors(fieldErrors);

        return ResponseEntity
                .badRequest()
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleException(Exception e) {
        log.error("Internal Server Error exception: ", e);
        ApiErrorDto apiErrorDto = new ApiErrorDto()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message(e.getMessage());
        return ResponseEntity
                .internalServerError()
                .body(apiErrorDto);
    }
}
