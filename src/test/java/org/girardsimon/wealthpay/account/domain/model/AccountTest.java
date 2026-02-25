package org.girardsimon.wealthpay.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCanceled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.exception.AccountHistoryNotFoundException;
import org.girardsimon.wealthpay.account.domain.exception.InvalidAccountEventStreamException;
import org.junit.jupiter.api.Test;

class AccountTest {

  @Test
  void rehydrate_requires_first_event_to_be_account_opened() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency currency = SupportedCurrency.USD;
    Money credit = Money.of(BigDecimal.valueOf(10L), currency);
    AccountEventMeta meta = AccountEventMeta.of(EventId.newId(), accountId, Instant.now(), 1L);
    AccountEvent fakeEvent = new FundsCredited(meta, TransactionId.newId(), credit);
    List<AccountEvent> history = List.of(fakeEvent);

    // Act ... Assert
    assertThatExceptionOfType(InvalidAccountEventStreamException.class)
        .isThrownBy(() -> Account.rehydrate(history));
  }

  @Test
  void rehydrate_requires_at_least_one_event() {
    // Arrange
    List<AccountEvent> history = Collections.emptyList();

    // Act ... Assert
    assertThatExceptionOfType(AccountHistoryNotFoundException.class)
        .isThrownBy(() -> Account.rehydrate(history));
  }

  @Test
  void rehydrateFromSnapshot_should_produce_same_state_as_full_replay() {
    // Arrange
    AccountId accountId = AccountId.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Instant now = Instant.now();
    ReservationId reservation1 = ReservationId.newId();
    ReservationId reservation2 = ReservationId.newId();

    AccountEventMeta meta1 = AccountEventMeta.of(EventId.newId(), accountId, now, 1L);
    AccountEventMeta meta2 = AccountEventMeta.of(EventId.newId(), accountId, now, 2L);
    AccountEventMeta meta3 = AccountEventMeta.of(EventId.newId(), accountId, now, 3L);
    AccountEventMeta meta4 = AccountEventMeta.of(EventId.newId(), accountId, now, 4L);
    AccountEventMeta meta5 = AccountEventMeta.of(EventId.newId(), accountId, now, 5L);
    AccountEventMeta meta6 = AccountEventMeta.of(EventId.newId(), accountId, now, 6L);
    AccountEventMeta meta7 = AccountEventMeta.of(EventId.newId(), accountId, now, 7L);

    List<AccountEvent> fullHistory =
        List.of(
            new AccountOpened(meta1, usd, Money.of(new BigDecimal("500.00"), usd)),
            new FundsCredited(
                meta2, TransactionId.newId(), Money.of(new BigDecimal("200.00"), usd)),
            new FundsReserved(
                meta3, TransactionId.newId(), reservation1, Money.of(new BigDecimal("50.00"), usd)),
            new ReservationCaptured(meta4, reservation1, Money.of(new BigDecimal("50.00"), usd)),
            new FundsReserved(
                meta5, TransactionId.newId(), reservation2, Money.of(new BigDecimal("30.00"), usd)),
            new ReservationCanceled(meta6, reservation2, Money.of(new BigDecimal("30.00"), usd)),
            new FundsDebited(
                meta7, TransactionId.newId(), Money.of(new BigDecimal("100.00"), usd)));
    int snapshotAt = 4;
    List<AccountEvent> eventsBeforeSnapshot = fullHistory.subList(0, snapshotAt);
    List<AccountEvent> eventsAfterSnapshot = fullHistory.subList(snapshotAt, fullHistory.size());
    Account referenceAccount = Account.rehydrate(fullHistory);
    Account snapshotSource = Account.rehydrate(eventsBeforeSnapshot);
    AccountSnapshot snapshot = Account.toSnapshot(snapshotSource);

    // Act
    Account restoredAccount = Account.rehydrateFromSnapshot(snapshot, eventsAfterSnapshot);

    // Assert
    assertAll(
        () -> assertThat(restoredAccount.getBalance()).isEqualTo(referenceAccount.getBalance()),
        () -> assertThat(restoredAccount.getStatus()).isEqualTo(referenceAccount.getStatus()),
        () -> assertThat(restoredAccount.getVersion()).isEqualTo(referenceAccount.getVersion()),
        () ->
            assertThat(restoredAccount.getReservations())
                .isEqualTo(referenceAccount.getReservations()),
        () -> assertThat(restoredAccount.getCurrency()).isEqualTo(referenceAccount.getCurrency()),
        () -> assertThat(restoredAccount.getId()).isEqualTo(referenceAccount.getId()));
  }
}
