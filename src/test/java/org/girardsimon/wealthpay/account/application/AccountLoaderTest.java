package org.girardsimon.wealthpay.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountSnapshot;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.EventId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountLoaderTest {

  @Mock AccountEventStore accountEventStore;
  @Mock AccountSnapshotStore accountSnapshotStore;

  @InjectMocks AccountLoader accountLoader;

  @Test
  void loadAccount_should_rehydrate_all_account_events_when_no_snapshot_exists() {
    // Arrange
    AccountId accountId = AccountId.newId();
    when(accountSnapshotStore.load(accountId)).thenReturn(Optional.empty());
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(new BigDecimal("10.00"), usd);
    AccountEventMeta meta = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountOpened accountOpened = new AccountOpened(meta, usd, initialBalance);
    when(accountEventStore.loadEvents(accountId)).thenReturn(List.of(accountOpened));

    // Act
    Account account = accountLoader.loadAccount(accountId);

    // Assert
    assertAll(
        () -> assertThat(account.getId()).isEqualTo(accountId),
        () -> assertThat(account.getBalance()).isEqualTo(initialBalance),
        () -> assertThat(account.getReservations()).isEmpty(),
        () -> assertThat(account.getCurrency()).isEqualTo(usd),
        () -> assertThat(account.getVersion()).isEqualTo(1L));
  }

  @Test
  void loadAccount_should_rehydrate_account_events_after_snapshot_when_snapshot_is_found() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money balance = Money.of(new BigDecimal("5555555.00"), usd);
    Map<ReservationId, Money> reservations =
        Map.of(ReservationId.newId(), Money.of(new BigDecimal("15.00"), usd));
    long version = 400L;
    AccountSnapshot snapshot =
        new AccountSnapshot(accountId, usd, balance, AccountStatus.OPENED, reservations, version);
    when(accountSnapshotStore.load(accountId)).thenReturn(Optional.of(snapshot));
    Money credit = Money.of(new BigDecimal("10.00"), usd);
    AccountEventMeta meta = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 401L);
    FundsCredited fundsCredited = new FundsCredited(meta, TransactionId.newId(), credit);
    when(accountEventStore.loadEventsAfterVersion(accountId, version))
        .thenReturn(List.of(fundsCredited));

    // Act
    Account account = accountLoader.loadAccount(accountId);

    // Assert
    assertAll(
        () -> assertThat(account.getId()).isEqualTo(accountId),
        () ->
            assertThat(account.getBalance()).isEqualTo(Money.of(new BigDecimal("5555565.00"), usd)),
        () -> assertThat(account.getReservations()).isEqualTo(reservations),
        () -> assertThat(account.getCurrency()).isEqualTo(usd),
        () -> assertThat(account.getVersion()).isEqualTo(401L));
  }
}
