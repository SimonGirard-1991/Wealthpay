package org.girardsimon.wealthpay.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountReadServiceTest {

  @Mock AccountBalanceReader accountBalanceReader;

  @InjectMocks AccountReadService accountReadService;

  @Test
  void getAccountBalance_should_return_account_balance_view_for_given_id() {
    // Arrange
    AccountId uuid = AccountId.newId();
    AccountBalanceView mock = mock(AccountBalanceView.class);
    when(accountBalanceReader.getAccountBalance(uuid)).thenReturn(mock);

    // Act
    AccountBalanceView accountBalanceView = accountReadService.getAccountBalance(uuid);

    // Assert
    assertThat(accountBalanceView).isEqualTo(mock);
  }
}
