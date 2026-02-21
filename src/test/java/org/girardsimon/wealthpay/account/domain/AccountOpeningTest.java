package org.girardsimon.wealthpay.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.exception.AccountCurrencyMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.InvalidInitialBalanceException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.HandleResult;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.testsupport.TestEventIdGenerator;
import org.junit.jupiter.api.Test;

class AccountOpeningTest {

  private final EventIdGenerator eventIdGenerator = new TestEventIdGenerator();

  @Test
  void openAccountCommand_produces_accountOpenedEvent() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
    OpenAccount openAccount = new OpenAccount(currency, initialBalance);

    // Act
    HandleResult result = Account.handle(openAccount, accountId, eventIdGenerator, Instant.now());
    List<AccountEvent> events = result.events();
    Account account = Account.rehydrate(events);

    // Assert
    assertAll(
        () -> assertThat(events).hasSize(1),
        () -> assertThat(events.getFirst()).isInstanceOf(AccountOpened.class),
        () -> assertThat(account.getId()).isEqualTo(accountId),
        () -> assertThat(account.getCurrency()).isEqualTo(currency),
        () -> assertThat(account.getStatus()).isEqualTo(AccountStatus.OPENED),
        () -> assertThat(account.getBalance()).isEqualTo(initialBalance),
        () -> assertThat(account.getVersion()).isEqualTo(1L));
  }

  @Test
  void openAccountCommand_requires_initial_balance_in_same_currency_as_currency_of_account() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    SupportedCurrency eur = SupportedCurrency.EUR;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    OpenAccount openAccount = new OpenAccount(eur, initialBalance);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountCurrencyMismatchException.class)
        .isThrownBy(() -> Account.handle(openAccount, accountId, eventIdGenerator, occurredAt));
  }

  @Test
  void openAccountCommand_does_not_permit_negative_initial_balance() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(-10L), currency);
    OpenAccount openAccount = new OpenAccount(currency, initialBalance);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(InvalidInitialBalanceException.class)
        .isThrownBy(() -> Account.handle(openAccount, accountId, eventIdGenerator, occurredAt));
  }

  @Test
  void openAccountCommand_permits_zero_initial_balance() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.ZERO, currency);
    OpenAccount openAccount = new OpenAccount(currency, initialBalance);

    // Act
    HandleResult result = Account.handle(openAccount, accountId, eventIdGenerator, Instant.now());
    List<AccountEvent> accountEvents = result.events();

    // Assert
    assertThat(accountEvents).hasSize(1);
    Account account = Account.rehydrate(accountEvents);
    assertThat(account.getBalance()).isEqualTo(initialBalance);
  }
}
