package org.girardsimon.wealthpay.account.application;

import org.girardsimon.wealthpay.account.application.response.CaptureReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReservationCaptureStatus;
import org.girardsimon.wealthpay.account.application.view.AccountBalanceView;
import org.girardsimon.wealthpay.account.domain.command.CaptureReservation;
import org.girardsimon.wealthpay.account.domain.command.OpenAccount;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.exception.AccountHistoryNotFound;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceTest {

    AccountEventStore accountEventStore = mock(AccountEventStore.class);
    AccountBalanceProjector accountBalanceProjector = mock(AccountBalanceProjector.class);

    Clock clock = Clock.fixed(
            Instant.parse("2025-11-16T15:00:00Z"),
            ZoneOffset.UTC
    );

    AccountId accountId = AccountId.newId();

    AccountIdGenerator accountIdGenerator = () -> accountId;

    AccountApplicationService accountApplicationService = new AccountApplicationService(
            accountEventStore,
            accountBalanceProjector,
            clock,
            accountIdGenerator
    );

    @Test
    void openAccount_saves_event_AccountOpened_when_account_does_not_exist() {
        // Arrange
        SupportedCurrency currency = SupportedCurrency.USD;
        Money initialBalance = new Money(BigDecimal.valueOf(10L), currency);
        OpenAccount openAccount = new OpenAccount(currency, initialBalance);
        when(accountEventStore.loadEvents(accountId)).thenReturn(List.of());

        // Act
        accountApplicationService.openAccount(openAccount);

        // Assert
        AccountOpened accountOpened = new AccountOpened(
                accountId,
                Instant.parse("2025-11-16T15:00:00Z"),
                1L,
                currency,
                initialBalance
        );
        InOrder inOrder = inOrder(accountEventStore, accountBalanceProjector);
        inOrder.verify(accountEventStore).appendEvents(accountId, 0L, List.of(accountOpened));
        inOrder.verify(accountBalanceProjector).project(List.of(accountOpened));
    }

    @Test
    void getAccountBalance_should_return_account_balance_view_for_given_id() {
        // Arrange
        AccountId uuid = AccountId.newId();
        AccountBalanceView mock = mock(AccountBalanceView.class);
        when(accountBalanceProjector.getAccountBalance(uuid)).thenReturn(mock);

        // Act
        AccountBalanceView accountBalanceView = accountApplicationService.getAccountBalance(uuid);

        // Assert
        assertThat(accountBalanceView).isEqualTo(mock);
        verifyNoInteractions(accountEventStore);
    }

    @Test
    void captureReservation_should_save_reservation_captured_event_when_reservation_exists() {
        // Arrange
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
        AccountOpened accountOpened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                usd,
                initialBalance
        );
        Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
        ReservationId reservationId = ReservationId.newId();
        FundsReserved fundsReserved = new FundsReserved(
                accountId,
                Instant.now(),
                2L,
                reservationId,
                reservedAmount
        );
        List<AccountEvent> accountEvents = List.of(accountOpened, fundsReserved);
        when(accountEventStore.loadEvents(accountId)).thenReturn(accountEvents);
        CaptureReservation captureReservation = new CaptureReservation(accountId, reservationId);

        // Act
        CaptureReservationResponse captureReservationResponse = accountApplicationService.captureReservation(captureReservation);

        // Assert
        ReservationCaptured reservationCaptured = new ReservationCaptured(
                accountId,
                reservationId,
                reservedAmount,
                3L,
                Instant.parse("2025-11-16T15:00:00Z")
        );
        InOrder inOrder = inOrder(accountEventStore, accountBalanceProjector);
        inOrder.verify(accountEventStore).appendEvents(accountId, 2L, List.of(reservationCaptured));
        inOrder.verify(accountBalanceProjector).project(List.of(reservationCaptured));
        assertAll(
                () -> assertThat(captureReservationResponse.accountId()).isEqualTo(accountId),
                () -> assertThat(captureReservationResponse.reservationId()).isEqualTo(reservationId),
                () -> assertThat(captureReservationResponse.reservationCaptureStatus()).isEqualTo(ReservationCaptureStatus.CAPTURED),
                () -> assertThat(captureReservationResponse.money()).isEqualTo(reservedAmount)
        );


    }


    @Test
    void captureReservation_should_not_save_data_when_no_reservation_found() {
        // Arrange
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(10L), usd);
        AccountOpened accountOpened = new AccountOpened(
                accountId,
                Instant.now(),
                1L,
                usd,
                initialBalance
        );
        Money reservedAmount = Money.of(BigDecimal.valueOf(5L), usd);
        ReservationId reservationId = ReservationId.newId();
        FundsReserved fundsReserved = new FundsReserved(
                accountId,
                Instant.now(),
                2L,
                reservationId,
                reservedAmount
        );
        List<AccountEvent> accountEvents = List.of(accountOpened, fundsReserved);
        when(accountEventStore.loadEvents(accountId)).thenReturn(accountEvents);
        ReservationId otherReservationId = ReservationId.newId();
        CaptureReservation captureReservation = new CaptureReservation(accountId, otherReservationId);

        // Act
        CaptureReservationResponse captureReservationResponse = accountApplicationService.captureReservation(captureReservation);

        // Assert
        ReservationCaptured reservationCaptured = new ReservationCaptured(
                accountId,
                reservationId,
                reservedAmount,
                3L,
                Instant.parse("2025-11-16T15:00:00Z")
        );
        verify(accountEventStore, times(0)).appendEvents(any(), anyLong(), any());
        verify(accountBalanceProjector, times(0)).project(List.of(reservationCaptured));
        assertAll(
                () -> assertThat(captureReservationResponse.accountId()).isEqualTo(accountId),
                () -> assertThat(captureReservationResponse.reservationId()).isEqualTo(otherReservationId),
                () -> assertThat(captureReservationResponse.reservationCaptureStatus()).isEqualTo(ReservationCaptureStatus.NO_EFFECT),
                () -> assertThat(captureReservationResponse.money()).isNull()
        );
    }

    @Test
    void captureReservation_should_throw_account_history_not_found_when_no_corresponding_account() {
        // Arrange
        CaptureReservation captureReservation = new CaptureReservation(accountId, ReservationId.newId());
        when(accountEventStore.loadEvents(accountId)).thenReturn(List.of());

        // Act ... Assert
        assertThatExceptionOfType(AccountHistoryNotFound.class)
                .isThrownBy(() -> accountApplicationService.captureReservation(captureReservation));
    }
}