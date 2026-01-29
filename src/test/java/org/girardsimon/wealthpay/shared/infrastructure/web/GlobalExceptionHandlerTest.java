package org.girardsimon.wealthpay.shared.infrastructure.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FakeController.class)
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

  @MockitoBean FakeService fakeService;

  @Autowired MockMvc mockMvc;

  public static Stream<Arguments> allBadRequestExceptions() {
    HttpMessageNotReadableException httpMessageNotReadableException =
        new HttpMessageNotReadableException(
            "message",
            new HttpInputMessage() {
              @Override
              public InputStream getBody() {
                return InputStream.nullInputStream();
              }

              @Override
              public HttpHeaders getHeaders() {
                return HttpHeaders.EMPTY;
              }
            });
    return Stream.of(
        Arguments.of(new IllegalArgumentException("Illegal Argument"), "Illegal Argument"),
        Arguments.of(httpMessageNotReadableException, "message"));
  }

  public static Stream<Arguments> allConflictExceptions() {
    return Stream.of(Arguments.of(new OptimisticLockingFailureException("message"), "message"));
  }

  public static Stream<Arguments> allInternalServerErrorExceptions() {
    return Stream.of(Arguments.of(new IllegalStateException("message"), "message"));
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
