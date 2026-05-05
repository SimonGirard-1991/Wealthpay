package org.girardsimon.wealthpay.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.girardsimon.wealthpay.account.domain.command.CloseAccount;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.HandleResult;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.testsupport.TestAccountIdGenerator;
import org.girardsimon.wealthpay.account.testsupport.TestEventIdGenerator;
import org.junit.jupiter.api.Test;

class AccountClosingTest {

  private final AccountIdGenerator accountIdGenerator = new TestAccountIdGenerator();
  private final EventIdGenerator eventIdGenerator = new TestEventIdGenerator();

  @Test
  void closeAccount_emits_AccountClosed_event_and_set_status_to_CLOSED() {
    // Arrange
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
    AccountEventMeta meta1 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, currency, initialBalance);
    AccountEventMeta meta2 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 2L);
    FundsDebited debited =
        new FundsDebited(meta2, TransactionId.of(UUID.randomUUID()), initialBalance);
    List<AccountEvent> initEvents = List.of(opened, debited);
    Account account = Account.rehydrate(initEvents);
    CloseAccount closeAccount = new CloseAccount(accountId);

    // Act
    List<AccountEvent> closingEvents =
        account.handle(closeAccount, eventIdGenerator, Instant.now()).events();
    List<AccountEvent> allEvents =
        Stream.concat(initEvents.stream(), closingEvents.stream()).toList();
    Account accountAfterCredit = Account.rehydrate(allEvents);

    // Assert
    assertAll(
        () -> assertThat(allEvents).hasSize(3),
        () -> assertThat(allEvents.getLast()).isInstanceOf(AccountClosed.class),
        () -> assertThat(allEvents.getLast().version()).isEqualTo(3L),
        () -> assertThat(accountAfterCredit.getBalance().isZero()).isTrue(),
        () -> assertThat(accountAfterCredit.getStatus()).isEqualTo(AccountStatus.CLOSED),
        () -> assertThat(accountAfterCredit.getVersion()).isEqualTo(3L));
  }

  @Test
  void closeAccount_requires_same_id_as_account() {
    // Arrange
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
    AccountEventMeta meta1 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, currency, initialBalance);
    AccountEventMeta meta2 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 2L);
    FundsDebited debited =
        new FundsDebited(meta2, TransactionId.of(UUID.randomUUID()), initialBalance);
    Account account = Account.rehydrate(List.of(opened, debited));
    AccountId otherAccountId = accountIdGenerator.newId();
    CloseAccount closeAccount = new CloseAccount(otherAccountId);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountIdMismatchException.class)
        .isThrownBy(() -> account.handle(closeAccount, eventIdGenerator, occurredAt));
  }

  @Test
  void closeAccount_should_be_idempotent_if_account_is_already_closed() {
    // Arrange
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
    AccountEventMeta meta1 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, currency, initialBalance);
    AccountEventMeta meta2 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 2L);
    FundsDebited debited =
        new FundsDebited(meta2, TransactionId.of(UUID.randomUUID()), initialBalance);
    AccountEventMeta meta3 =
        AccountEventMeta.of(eventIdGenerator.newId(), accountId, Instant.now(), 3L);
    AccountClosed closed = new AccountClosed(meta3);
    Account closedAccount = Account.rehydrate(List.of(opened, debited, closed));
    CloseAccount closeAccount = new CloseAccount(accountId);

    // Act
    HandleResult handleResult = closedAccount.handle(closeAccount, eventIdGenerator, Instant.now());

    // Assert
    assertThat(handleResult.hasEffect()).isFalse();
  }
}
