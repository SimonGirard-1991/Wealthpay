package org.girardsimon.wealthpay.account.infrastructure.web;

import org.girardsimon.wealthpay.account.api.generated.AccountsApi;
import org.girardsimon.wealthpay.account.api.generated.model.AccountResponseDto;
import org.girardsimon.wealthpay.account.api.generated.model.OpenAccountRequestDto;
import org.girardsimon.wealthpay.account.api.generated.model.OpenAccountResponseDto;
import org.girardsimon.wealthpay.account.application.AccountApplicationService;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.AccountBalanceViewDomainToDtoMapper;
import org.girardsimon.wealthpay.account.infrastructure.web.mapper.OpenAccountDtoToDomainMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
public class OpenAccountController implements AccountsApi {

    private final AccountApplicationService accountApplicationService;

    private final OpenAccountDtoToDomainMapper openAccountDtoToDomainMapper;
    private final AccountBalanceViewDomainToDtoMapper accountBalanceViewDomainToDtoMapper;

    public OpenAccountController(AccountApplicationService accountApplicationService, OpenAccountDtoToDomainMapper openAccountDtoToDomainMapper, AccountBalanceViewDomainToDtoMapper accountBalanceViewDomainToDtoMapper) {
        this.accountApplicationService = accountApplicationService;
        this.openAccountDtoToDomainMapper = openAccountDtoToDomainMapper;
        this.accountBalanceViewDomainToDtoMapper = accountBalanceViewDomainToDtoMapper;
    }

    @Override
    public ResponseEntity<AccountResponseDto> getAccountById(UUID id) {
        AccountBalanceView accountBalance = accountApplicationService.getAccountBalance(AccountId.of(id));
        return ResponseEntity.ok(accountBalanceViewDomainToDtoMapper.apply(accountBalance));
    }

    @Override
    public ResponseEntity<OpenAccountResponseDto> openAccount(OpenAccountRequestDto openAccountRequestDto) {
        OpenAccount openAccount = openAccountDtoToDomainMapper.apply(openAccountRequestDto);
        AccountId accountId = accountApplicationService.openAccount(openAccount);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(accountId.id())
                .toUri();
        return ResponseEntity.created(location).body(new OpenAccountResponseDto().accountId(accountId.id()));
    }
}
