package org.girardsimon.wealthpay.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.girardsimon.wealthpay.account.domain.command.CloseAccount;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.exception.AccountIdMismatchException;
import org.girardsimon.wealthpay.account.domain.exception.AccountInactiveException;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.EventId;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.testsupport.TestEventIdGenerator;
import org.junit.jupiter.api.Test;

class AccountClosingTest {

  private final EventIdGenerator eventIdGenerator = new TestEventIdGenerator();

  @Test
  void closeAccount_emits_AccountClosed_event_and_set_status_to_CLOSED() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
    AccountEventMeta meta1 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, currency, initialBalance);
    AccountEventMeta meta2 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsDebited debited = new FundsDebited(meta2, TransactionId.newId(), initialBalance);
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
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
    AccountEventMeta meta1 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, currency, initialBalance);
    AccountEventMeta meta2 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsDebited debited = new FundsDebited(meta2, TransactionId.newId(), initialBalance);
    Account account = Account.rehydrate(List.of(opened, debited));
    AccountId otherAccountId = AccountId.newId();
    CloseAccount closeAccount = new CloseAccount(otherAccountId);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountIdMismatchException.class)
        .isThrownBy(() -> account.handle(closeAccount, eventIdGenerator, occurredAt));
  }

  @Test
  void closeAccount_requires_account_to_be_opened() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10L), currency);
    AccountEventMeta meta1 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened opened = new AccountOpened(meta1, currency, initialBalance);
    AccountEventMeta meta2 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 2L);
    FundsDebited debited = new FundsDebited(meta2, TransactionId.newId(), initialBalance);
    AccountEventMeta meta3 = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 3L);
    AccountClosed closed = new AccountClosed(meta3);
    Account closedAccount = Account.rehydrate(List.of(opened, debited, closed));
    CloseAccount closeAccount = new CloseAccount(accountId);

    // Act ... Assert
    Instant occurredAt = Instant.now();
    assertThatExceptionOfType(AccountInactiveException.class)
        .isThrownBy(() -> closedAccount.handle(closeAccount, eventIdGenerator, occurredAt));
  }
}
