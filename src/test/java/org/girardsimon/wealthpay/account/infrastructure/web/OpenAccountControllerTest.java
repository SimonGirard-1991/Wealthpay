package org.girardsimon.wealthpay.account.infrastructure.web;

import org.girardsimon.wealthpay.account.api.generated.model.AccountResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.AccountStatusDto;
import org.girardsimon.wealthpay.account.api.generated.model.OpenAccountRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.SupportedCurrencyDto;
import org.girardsimon.wealthpay.account.application.AccountApplicationService;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.AccountBalanceViewDomainToDtoMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.OpenAccountDtoToDomainMapper;
import org.girardsimon.wealthpay.shared.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OpenAccountController.class)
@Import(GlobalExceptionHandler.class)
class OpenAccountControllerTest {

    @MockitoBean
    AccountApplicationService accountApplicationService;

    @MockitoBean
    OpenAccountDtoToDomainMapper openAccountDtoToDomainMapper;

    @MockitoBean
    AccountBalanceViewDomainToDtoMapper accountBalanceViewDomainToDtoMapper;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void openAccount_returns_201_with_location_and_body() throws Exception {
        // Arrange
        OpenAccountRequestDto openAccountRequestDto = new OpenAccountRequestDto()
                .accountCurrency(SupportedCurrencyDto.USD)
                .initialAmount(BigDecimal.valueOf(100.50))
                .initialAmountCurrency(SupportedCurrencyDto.USD);
        OpenAccount openAccount = mock(OpenAccount.class);
        when(openAccountDtoToDomainMapper.apply(openAccountRequestDto)).thenReturn(openAccount);
        AccountId accountId = AccountId.newId();
        when(accountApplicationService.openAccount(openAccount)).thenReturn(accountId);

        // Act ... Assert
        mockMvc.perform(post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(openAccountRequestDto)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/accounts/" + accountId.id()))
                .andExpect(jsonPath("$.accountId").value(accountId.id().toString()));
    }

    @Test
    void openAccount_returns_structured_validation_error_when_body_invalid() throws Exception {
        // Arrange
        OpenAccountRequestDto openAccountRequestDto = new OpenAccountRequestDto()
                .accountCurrency(SupportedCurrencyDto.USD)
                .initialAmount(BigDecimal.valueOf(-100.50).setScale(2, RoundingMode.HALF_UP))
                .initialAmountCurrency(SupportedCurrencyDto.USD);

        // Act ... Assert
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openAccountRequestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("initialAmount"))
                .andExpect(jsonPath("$.errors[0].message").value("must be greater than or equal to 0"))
                .andExpect(jsonPath("$.errors[0].code").value("DecimalMin"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").value("-100.50"));
    }

    @Test
    void getAccountById_should_return_200_with_account_response() throws Exception {
        // Arrange
        UUID accountId = UUID.randomUUID();
        AccountBalanceView accountBalanceView = mock(AccountBalanceView.class);
        when(accountApplicationService.getAccountBalance(accountId)).thenReturn(accountBalanceView);
        when(accountBalanceViewDomainToDtoMapper.apply(accountBalanceView)).thenReturn(new AccountResponseDto()
                .id(accountId)
                .balanceAmount(BigDecimal.valueOf(100.50).setScale(2, RoundingMode.HALF_EVEN))
                .currency(SupportedCurrencyDto.USD)
                .status(AccountStatusDto.OPENED));

        // Act ... Assert
        mockMvc.perform(get("/accounts/{id}", accountId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.balanceAmount").value("100.5"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("OPENED"));

    }
}