package org.girardsimon.wealthpay.account.infrastructure.web;

import java.net.URI;
import java.util.UUID;
import org.girardsimon.wealthpay.account.api.generated.AccountApi;
import org.girardsimon.wealthpay.account.api.generated.model.AccountResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.OpenAccountRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.OpenAccountResponseDto;
import org.girardsimon.wealthpay.account.application.AccountApplicationService;
import org.girardsimon.wealthpay.account.application.AccountReadService;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.AccountBalanceViewDomainToDtoMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.OpenAccountDtoToDomainMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class AccountController implements AccountApi {

  private final AccountApplicationService accountApplicationService;
  private final AccountReadService accountReadService;

  private final OpenAccountDtoToDomainMapper openAccountDtoToDomainMapper;
  private final AccountBalanceViewDomainToDtoMapper accountBalanceViewDomainToDtoMapper;

  public AccountController(
      AccountApplicationService accountApplicationService,
      AccountReadService accountReadService,
      OpenAccountDtoToDomainMapper openAccountDtoToDomainMapper,
      AccountBalanceViewDomainToDtoMapper accountBalanceViewDomainToDtoMapper) {
    this.accountApplicationService = accountApplicationService;
    this.accountReadService = accountReadService;
    this.openAccountDtoToDomainMapper = openAccountDtoToDomainMapper;
    this.accountBalanceViewDomainToDtoMapper = accountBalanceViewDomainToDtoMapper;
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
