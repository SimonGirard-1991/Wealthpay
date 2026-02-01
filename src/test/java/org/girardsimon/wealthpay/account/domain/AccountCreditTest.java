package org.girardsimon.wealthpay.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.girardsimon.wealthpay.account.domain.command.CreditAccount;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.exception.AccountCurrencyMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.exception.AmountMustBePositiveException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.junit.jupiter.api.Test;

class AccountCreditTest {

  @Test
  void creditAccount_emits_FundsCredited_event_and_updates_account_balance() {
    // Arrange
    TransactionId transactionId = TransactionId.newId();
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
    OpenAccount openAccount = new OpenAccount(currency, initialBalance);
    Money creditAmount = Money.of(BigDecimal.valueOf(5L), currency);
    CreditAccount creditAccount = new CreditAccount(transactionId, accountId, creditAmount);

    // Act
    List<AccountEvent> openingEvents = Account.handle(openAccount, accountId, Instant.now());
    Account account = Account.rehydrate(openingEvents);
    List<AccountEvent> creditEvents = account.handle(creditAccount, Instant.now());
    List<AccountEvent> allEvents =
        Stream.concat(openingEvents.stream(), creditEvents.stream()).toList();
    Account accountAfterCredit = Account.rehydrate(allEvents);

    // Assert
    Money expectedBalance = Money.of(BigDecimal.valueOf(15L), currency);
    AccountEvent lastEvent = allEvents.getLast();
    assertThat(lastEvent).isInstanceOf(FundsCredited.class);
    FundsCredited fundsCredited = (FundsCredited) lastEvent;

    assertAll(
        () -> assertThat(allEvents).hasSize(2),
        () -> assertThat(fundsCredited.transactionId()).isEqualTo(transactionId),
        () -> assertThat(fundsCredited.version()).isEqualTo(2L),
        () -> assertThat(accountAfterCredit.getBalance()).isEqualTo(expectedBalance),
        () -> assertThat(accountAfterCredit.getStatus()).isEqualTo(AccountStatus.OPENED),
        () -> assertThat(accountAfterCredit.getVersion()).isEqualTo(2L));
  }

  @Test
  void creditAccount_requires_same_currency_as_account() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountOpened accountOpened =
        new AccountOpened(accountId, Instant.now(), 1L, usd, initialBalance);
    Account account = Account.rehydrate(List.of(accountOpened));
    SupportedCurrency chf = SupportedCurrency.CHF;
    Money creditAmount = Money.of(BigDecimal.valueOf(5L), chf);
    CreditAccount creditAccount = new CreditAccount(TransactionId.newId(), accountId, creditAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountCurrencyMismatchException.class)
        .isThrownBy(() -> account.handle(creditAccount, occurredAt));
  }

  @Test
  void creditAccount_requires_same_id_as_account() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
    AccountOpened accountOpened =
        new AccountOpened(accountId, Instant.now(), 1L, currency, initialBalance);
    Account account = Account.rehydrate(List.of(accountOpened));
    Money creditAmount = Money.of(BigDecimal.valueOf(5L), currency);
    AccountId otherAccountId = AccountId.newId();
    CreditAccount creditAccount =
        new CreditAccount(TransactionId.newId(), otherAccountId, creditAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountIdMismatchException.class)
        .isThrownBy(() -> account.handle(creditAccount, occurredAt));
  }

  @Test
  void creditAccount_requires_strictly_positive_amount() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountOpened accountOpened =
        new AccountOpened(accountId, Instant.now(), 1L, usd, initialBalance);
    Account account = Account.rehydrate(List.of(accountOpened));
    Money creditAmount = Money.of(BigDecimal.valueOf(-10L), usd);
    CreditAccount creditAccount = new CreditAccount(TransactionId.newId(), accountId, creditAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AmountMustBePositiveException.class)
        .isThrownBy(() -> account.handle(creditAccount, occurredAt));
  }

  @Test
  void creditAccount_requires_account_to_be_opened() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountOpened opened = new AccountOpened(accountId, Instant.now(), 1L, usd, initialBalance);
    FundsDebited debited =
        new FundsDebited(TransactionId.newId(), accountId, Instant.now(), 2L, initialBalance);
    AccountClosed closed = new AccountClosed(accountId, Instant.now(), 3L);
    Account closedAccount = Account.rehydrate(List.of(opened, debited, closed));
    Money creditAmount = Money.of(BigDecimal.valueOf(10L), usd);
    CreditAccount creditAccount = new CreditAccount(TransactionId.newId(), accountId, creditAmount);

    // Act + Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountInactiveException.class)
        .isThrownBy(() -> closedAccount.handle(creditAccount, occurredAt));
  }
}
