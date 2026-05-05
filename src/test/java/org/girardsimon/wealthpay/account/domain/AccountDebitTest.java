package org.girardsimon.wealthpay.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.girardsimon.wealthpay.account.domain.command.DebitAccount;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.exception.AccountCurrencyMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.exception.AmountMustBePositiveException;
import org.girardsimon.wealthpay.account.domain.exception.InsufficientFundsException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.testsupport.TestAccountIdGenerator;
import org.girardsimon.wealthpay.account.testsupport.TestEventIdGenerator;
import org.junit.jupiter.api.Test;

class AccountDebitTest {

  private final AccountIdGenerator accountIdGenerator = new TestAccountIdGenerator();
  private final EventIdGenerator eventIdGenerator = new TestEventIdGenerator();

  @Test
  void debitAccount_emits_FundsDebited_event_and_updates_account_balance() {
    // Arrange
    TransactionId transactionId = TransactionId.of(UUID.randomUUID());
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
    OpenAccount openAccount = new OpenAccount(currency, initialBalance);
    Money debitAmount = Money.of(BigDecimal.valueOf(5L), currency);
    DebitAccount debitAccount = new DebitAccount(transactionId, accountId, debitAmount);

    // Act
    List<AccountEvent> openingEvents =
        Account.handle(openAccount, accountId, eventIdGenerator, Instant.now()).events();
    Account account = Account.rehydrate(openingEvents);
    List<AccountEvent> debitEvents =
        account.handle(debitAccount, eventIdGenerator, Instant.now()).events();
    List<AccountEvent> allEvents =
        Stream.concat(openingEvents.stream(), debitEvents.stream()).toList();
    Account accountAfterCredit = Account.rehydrate(allEvents);

    // Assert
    Money expectedBalance = Money.of(BigDecimal.valueOf(5L), currency);
    AccountEvent lastEvent = allEvents.getLast();
    assertThat(lastEvent).isInstanceOf(FundsDebited.class);
    FundsDebited fundsDebited = (FundsDebited) lastEvent;
    assertAll(
        () -> assertThat(allEvents).hasSize(2),
        () -> assertThat(fundsDebited.transactionId()).isEqualTo(transactionId),
        () -> assertThat(fundsDebited.version()).isEqualTo(2L),
        () -> assertThat(accountAfterCredit.getBalance()).isEqualTo(expectedBalance),
        () -> assertThat(accountAfterCredit.getStatus()).isEqualTo(AccountStatus.OPENED),
        () -> assertThat(accountAfterCredit.getVersion()).isEqualTo(2L));
  }

  @Test
  void debitAccount_requires_same_currency_as_account() {
    // Arrange
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta meta =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(meta, usd, initialBalance);
    Account account = Account.rehydrate(List.of(accountOpened));
    SupportedCurrency chf = SupportedCurrency.CHF;
    Money debitAmount = Money.of(BigDecimal.valueOf(5L), chf);
    DebitAccount debitAccount =
        new DebitAccount(TransactionId.of(UUID.randomUUID()), accountId, debitAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountCurrencyMismatchException.class)
        .isThrownBy(() -> account.handle(debitAccount, eventIdGenerator, occurredAt));
  }

  @Test
  void debitAccount_requires_same_id_as_account() {
    // Arrange
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta meta =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(meta, usd, initialBalance);
    Account account = Account.rehydrate(List.of(accountOpened));
    Money debitAmount = Money.of(BigDecimal.valueOf(5L), usd);
    AccountId otherAccountId = accountIdGenerator.newId();
    DebitAccount debitAccount =
        new DebitAccount(TransactionId.of(UUID.randomUUID()), otherAccountId, debitAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountIdMismatchException.class)
        .isThrownBy(() -> account.handle(debitAccount, eventIdGenerator, occurredAt));
  }

  @Test
  void debitAccount_requires_strictly_positive_amount() {
    // Arrange
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta meta =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(meta, usd, initialBalance);
    Account account = Account.rehydrate(List.of(accountOpened));
    Money debitAmount = Money.of(BigDecimal.valueOf(-5L), usd);
    DebitAccount debitAccount =
        new DebitAccount(TransactionId.of(UUID.randomUUID()), accountId, debitAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AmountMustBePositiveException.class)
        .isThrownBy(() -> account.handle(debitAccount, eventIdGenerator, occurredAt));
  }

  @Test
  void debitAccount_requires_account_to_be_opened() {
    // Arrange
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta meta1 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, usd, initialBalance);
    AccountEventMeta meta2 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 2L);
    FundsDebited debited =
        new FundsDebited(meta2, TransactionId.of(UUID.randomUUID()), initialBalance);
    AccountEventMeta meta3 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 3L);
    AccountClosed closed = new AccountClosed(meta3);
    Account closedAccount = Account.rehydrate(List.of(opened, debited, closed));
    Money debitAmount = Money.of(BigDecimal.valueOf(5L), usd);
    DebitAccount debitAccount =
        new DebitAccount(TransactionId.of(UUID.randomUUID()), accountId, debitAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountInactiveException.class)
        .isThrownBy(() -> closedAccount.handle(debitAccount, eventIdGenerator, occurredAt));
  }

  @Test
  void debitAccount_requires_resulting_balance_to_be_positive() {
    // Arrange
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
    AccountEventMeta meta =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(meta, usd, initialBalance);
    Account account = Account.rehydrate(List.of(accountOpened));
    Money debitAmount = Money.of(BigDecimal.valueOf(15L), usd);
    DebitAccount debitAccount =
        new DebitAccount(TransactionId.of(UUID.randomUUID()), accountId, debitAmount);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(InsufficientFundsException.class)
        .isThrownBy(() -> account.handle(debitAccount, eventIdGenerator, occurredAt));
  }
}
