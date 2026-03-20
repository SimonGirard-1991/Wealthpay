package org.girardsimon.wealthpay.account.infrastructure.web;

import java.net.URI;
import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.AccountApi;
import org.girardsimon.wealthpay.account.api.generated.model.AccountResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.CloseAccountResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.OpenAccountRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.OpenAccountResponseDto;
import org.girardsimon.wealthpay.account.application.AccountApplicationService;
import org.girardsimon.wealthpay.account.application.AccountReadService;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.command.CloseAccount;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.AccountBalanceViewDomainToDtoMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.CloseAccountDtoToDomainMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.CloseAccountResponseToDtoMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.OpenAccountDtoToDomainMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class AccountController implements AccountApi {

  private final AccountApplicationService accountApplicationService;
  private final AccountReadService accountReadService;

  private final OpenAccountDtoToDomainMapper openAccountDtoToDomainMapper;
  private final CloseAccountDtoToDomainMapper closeAccountDtoToDomainMapper;
  private final AccountBalanceViewDomainToDtoMapper accountBalanceViewDomainToDtoMapper;
  private final CloseAccountResponseToDtoMapper closeAccountResponseToDtoMapper;

  public AccountController(
      AccountApplicationService accountApplicationService,
      AccountReadService accountReadService,
      OpenAccountDtoToDomainMapper openAccountDtoToDomainMapper,
      CloseAccountDtoToDomainMapper closeAccountDtoToDomainMapper,
      AccountBalanceViewDomainToDtoMapper accountBalanceViewDomainToDtoMapper,
      CloseAccountResponseToDtoMapper closeAccountResponseToDtoMapper) {
    this.accountApplicationService = accountApplicationService;
    this.accountReadService = accountReadService;
    this.openAccountDtoToDomainMapper = openAccountDtoToDomainMapper;
    this.closeAccountDtoToDomainMapper = closeAccountDtoToDomainMapper;
    this.accountBalanceViewDomainToDtoMapper = accountBalanceViewDomainToDtoMapper;
    this.closeAccountResponseToDtoMapper = closeAccountResponseToDtoMapper;
  }

  @Override
  public ResponseEntity<CloseAccountResponseDto> closeAccount(UUID id) {
    CloseAccount closeAccount = closeAccountDtoToDomainMapper.apply(id);
    TransactionStatus transactionStatus = accountApplicationService.closeAccount(closeAccount);
    return ResponseEntity.ok(closeAccountResponseToDtoMapper.apply(transactionStatus));
  }

  @Override
  public ResponseEntity<AccountResponseDto> getAccountById(UUID id) {
    AccountBalanceView accountBalance = accountReadService.getAccountBalance(AccountId.of(id));
    return ResponseEntity.ok(accountBalanceViewDomainToDtoMapper.apply(accountBalance));
  }

  @Override
  public ResponseEntity<OpenAccountResponseDto> openAccount(
      OpenAccountRequestDto openAccountRequestDto) {
    OpenAccount openAccount = openAccountDtoToDomainMapper.apply(openAccountRequestDto);
    AccountId accountId = accountApplicationService.openAccount(openAccount);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(accountId.id())
            .toUri();
    return ResponseEntity.created(location)
        .body(new OpenAccountResponseDto().accountId(accountId.id()));
  }
}
