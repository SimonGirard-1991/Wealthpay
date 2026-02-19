package org.girardsimon.wealthpay.account.infrastructure.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.model.CreditAccountRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.DebitAccountRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.application.AccountApplicationService;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.CreditAccountDtoToDomainMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.DebitAccountDtoToDomainMapper;
import org.girardsimon.wealthpay.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AccountTransactionController.class)
@Import(GlobalExceptionHandler.class)
class AccountTransactionControllerTest {

  @MockitoBean AccountApplicationService accountApplicationService;

  @MockitoBean CreditAccountDtoToDomainMapper creditAccountDtoToDomainMapper;

  @MockitoBean DebitAccountDtoToDomainMapper debitAccountDtoToDomainMapper;

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @Test
  void creditAccount_should_return_200_with_transaction_status() throws Exception {
    // Arrange
    UUID accountId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();
    CreditAccountRequestDto creditAccountRequestDto =
        new CreditAccountRequestDto()
            .amount(BigDecimal.valueOf(100.50))
            .currency(SupportedCurrencyDto.USD);
    CreditAccount creditAccount = mock(CreditAccount.class);
    when(creditAccountDtoToDomainMapper.apply(accountId, transactionId, creditAccountRequestDto))
        .thenReturn(creditAccount);
    when(accountApplicationService.creditAccount(creditAccount))
        .thenReturn(TransactionStatus.COMMITTED);

    // Act ... Assert
    mockMvc
        .perform(
            post("/accounts/{id}/deposits", accountId)
                .header("Transaction-Id", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(creditAccountRequestDto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMMITTED"));
  }

  @Test
  void debitAccount_should_return_200_with_transaction_status() throws Exception {
    // Arrange
    UUID accountId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();
    DebitAccountRequestDto debitAccountRequestDto =
        new DebitAccountRequestDto()
            .amount(BigDecimal.valueOf(100.50))
            .currency(SupportedCurrencyDto.USD);
    DebitAccount debitAccount = mock(DebitAccount.class);
    when(debitAccountDtoToDomainMapper.apply(accountId, transactionId, debitAccountRequestDto))
        .thenReturn(debitAccount);
    when(accountApplicationService.debitAccount(debitAccount))
        .thenReturn(TransactionStatus.COMMITTED);

    // Act ... Assert
    mockMvc
        .perform(
            post("/accounts/{id}/withdrawals", accountId)
                .header("Transaction-Id", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(debitAccountRequestDto)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMMITTED"));
  }
}
