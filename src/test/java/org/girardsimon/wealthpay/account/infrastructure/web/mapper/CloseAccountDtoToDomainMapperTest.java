package org.girardsimon.wealthpay.account.infrastructure.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.UUID;
import org.girardsimon.wealthpay.account.domain.command.CloseAccount;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.junit.jupiter.api.Test;

class CloseAccountDtoToDomainMapperTest {

  CloseAccountDtoToDomainMapper mapper = new CloseAccountDtoToDomainMapper();

  @Test
  void map_account_id_to_close_account_command() {
    // Arrange
    UUID accountId = UUID.randomUUID();

    // Act
    CloseAccount closeAccount = mapper.apply(accountId);

    // Assert
    assertAll(
        () -> assertThat(closeAccount.accountId()).isEqualTo(AccountId.of(accountId)),
        () -> assertThat(closeAccount.accountId().id()).isEqualTo(accountId));
  }
}
