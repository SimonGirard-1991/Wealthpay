package org.girardsimon.wealthpay.account.infrastructure.web;

import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.AccountTransactionApi;
import org.girardsimon.wealthpay.account.api.generated.model.CreditAccountRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.TransactionResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.TransactionStatusDto;
import org.girardsimon.wealthpay.account.application.AccountApplicationService;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.CreditAccountDtoToDomainMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountTransactionController implements AccountTransactionApi {

  private final AccountApplicationService accountApplicationService;

  private final CreditAccountDtoToDomainMapper creditAccountDtoToDomainMapper;

  public AccountTransactionController(
      AccountApplicationService accountApplicationService,
      CreditAccountDtoToDomainMapper creditAccountDtoToDomainMapper) {
    this.accountApplicationService = accountApplicationService;
    this.creditAccountDtoToDomainMapper = creditAccountDtoToDomainMapper;
  }

  @Override
  public ResponseEntity<TransactionResponseDto> creditAccount(
      UUID id, UUID transactionId, CreditAccountRequestDto creditAccountRequestDto) {
    CreditAccount creditAccount =
        creditAccountDtoToDomainMapper.apply(id, transactionId, creditAccountRequestDto);
    TransactionStatus transactionStatus = accountApplicationService.creditAccount(creditAccount);
    return ResponseEntity.ok(
        new TransactionResponseDto()
            .status(TransactionStatusDto.valueOf(transactionStatus.name())));
  }
}
