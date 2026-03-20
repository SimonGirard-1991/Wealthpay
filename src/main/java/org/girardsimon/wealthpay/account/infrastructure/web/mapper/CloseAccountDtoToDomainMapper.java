package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import java.util.UUID;
import java.util.function.Function;
import org.girardsimon.wealthpay.account.domain.command.CloseAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.springframework.stereotype.Component;

@Component
public class CloseAccountDtoToDomainMapper implements Function<UUID, CloseAccount> {

  @Override
  public CloseAccount apply(UUID accountId) {
    return new CloseAccount(AccountId.of(accountId));
  }
}
