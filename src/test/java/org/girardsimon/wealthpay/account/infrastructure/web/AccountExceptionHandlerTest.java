package org.girardsimon.wealthpay.account.infrastructure.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.stream.Stream;
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
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCanceledException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationAlreadyCapturedException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationNotFoundException;
import org.girardsimon.wealthpay.account.domain.exception.UnsupportedCurrencyException;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.shared.infrastructure.web.FakeController;
import org.girardsimon.wealthpay.shared.infrastructure.web.FakeService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FakeController.class)
@ActiveProfiles("test")
class AccountExceptionHandlerTest {

  @MockitoBean FakeService fakeService;

  @Autowired MockMvc mockMvc;

  private static Stream<Arguments> allBadRequestExceptions() {
    AccountId accountId1 = AccountId.newId();
    AccountId accountId2 = AccountId.newId();
    return Stream.of(
        Arguments.of(new IllegalArgumentException("Illegal Argument"), "Illegal Argument"),
        Arguments.of(
            new AccountIdMismatchException(accountId1, accountId2),
            "Account id mismatch: " + accountId1 + " vs " + accountId2));
  }

  private static Stream<Arguments> allNotFoundExceptions() {
    ReservationId reservationId = ReservationId.newId();
    AccountId accountId = AccountId.newId();
    return Stream.of(
        Arguments.of(
            new ReservationNotFoundException(reservationId),
            "Reservation not found: " + reservationId),
        Arguments.of(new AccountHistoryNotFound(), "Account history not found"),
        Arguments.of(
            new AccountBalanceNotFoundException(accountId),
            "No balance found for account " + accountId));
  }

  private static Stream<Arguments> allConflictExceptions() {
    return Stream.of(
        Arguments.of(new AccountInactiveException(), "Account is inactive"),
        Arguments.of(new ReservationAlreadyCanceledException("message"), "message"),
        Arguments.of(new ReservationAlreadyCapturedException("message"), "message"));
  }

  private static Stream<Arguments> allUnprocessableContentExceptions() {
    Money negativeAmount = Money.of(BigDecimal.valueOf(-100L), SupportedCurrency.USD);
    return Stream.of(
        Arguments.of(
            new InvalidInitialBalanceException(negativeAmount),
            "Initial balance must be strictly positive, got Money[amount=-100.00, currency=USD]"),
        Arguments.of(
            new AccountCurrencyMismatchException("USD", "EUR"),
            "Account currency USD does not match initial balance currency EUR"),
        Arguments.of(
            new AmountMustBePositiveException(negativeAmount),
            "Amount must be strictly positive, got Money[amount=-100.00, currency=USD]"),
        Arguments.of(
            new InsufficientFundsException(), "Insufficient funds to complete the operation"),
        Arguments.of(new AccountNotEmptyException(), "Account is not empty"),
        Arguments.of(new UnsupportedCurrencyException("BTC"), "Currency BTC is not supported"));
  }

  private static Stream<Arguments> allInternalServerErrorExceptions() {
    return Stream.of(Arguments.of(new InvalidAccountEventStreamException("message"), "message"));
  }

  @ParameterizedTest
  @MethodSource("allBadRequestExceptions")
  void all_bad_request_exceptions(Throwable throwable, String expectedMessage) throws Exception {
    // Arrange
    when(fakeService.fakeMethod()).thenThrow(throwable);

    // Act ... Assert
    mockMvc
        .perform(get("/fake"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value(expectedMessage));
  }

  @ParameterizedTest
  @MethodSource("allNotFoundExceptions")
  void all_not_found_exceptions(Throwable throwable, String expectedMessage) throws Exception {
    // Arrange
    when(fakeService.fakeMethod()).thenThrow(throwable);

    // Act ... Assert
    mockMvc
        .perform(get("/fake"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").value(expectedMessage));
  }

  @ParameterizedTest
  @MethodSource("allConflictExceptions")
  void all_conflict_exceptions(Throwable throwable, String expectedMessage) throws Exception {
    // Arrange
    when(fakeService.fakeMethod()).thenThrow(throwable);

    // Act ... Assert
    mockMvc
        .perform(get("/fake"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.message").value(expectedMessage));
  }

  @ParameterizedTest
  @MethodSource("allUnprocessableContentExceptions")
  void all_unprocessable_content_exceptions(Throwable throwable, String expectedMessage)
      throws Exception {
    // Arrange
    when(fakeService.fakeMethod()).thenThrow(throwable);

    // Act ... Assert
    mockMvc
        .perform(get("/fake"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.message").value(expectedMessage));
  }

  @ParameterizedTest
  @MethodSource("allInternalServerErrorExceptions")
  void all_internal_server_error_exceptions(Throwable throwable, String expectedMessage)
      throws Exception {
    // Arrange
    when(fakeService.fakeMethod()).thenThrow(throwable);

    // Act ... Assert
    mockMvc
        .perform(get("/fake"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.message").value(expectedMessage));
  }
}
