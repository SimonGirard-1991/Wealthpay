package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCancelled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.jooq.tables.pojos.EventStore;
import org.jooq.JSONB;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EventStoreEntryToAccountEventMapperTest {

    EventStoreEntryToAccountEventMapper mapper = new EventStoreEntryToAccountEventMapper(new ObjectMapper());

    public static Stream<Arguments> eventSourceAndExpectedEvent() {
        EventStore accountOpenedEvent = new EventStore();
        accountOpenedEvent.setEventType("AccountOpened");
        UUID accountId = UUID.randomUUID();
        accountOpenedEvent.setAccountId(accountId);
        accountOpenedEvent.setVersion(1L);
        accountOpenedEvent.setPayload(JSONB.valueOf("""
                {
                    "currency": "USD",
                    "initialBalance": 10.00,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """));
        AccountOpened accountOpened = new AccountOpened(
                AccountId.of(accountId),
                Instant.parse("2025-11-16T15:00:00Z"),
                1L,
                SupportedCurrency.USD,
                Money.of(BigDecimal.valueOf(10.00), SupportedCurrency.USD)
        );
        EventStore reservationCapturedEvent = new EventStore();
        reservationCapturedEvent.setEventType("ReservationCaptured");
        reservationCapturedEvent.setAccountId(accountId);
        reservationCapturedEvent.setVersion(4L);
        reservationCapturedEvent.setPayload(JSONB.valueOf("""
                {
                    "reservationId": "09518c66-ff5e-4596-9049-74dfbdf6f6db",
                    "currency": "EUR",
                    "amount": 40.00,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """));
        ReservationCaptured reservationCaptured = new ReservationCaptured(
                AccountId.of(accountId),
                ReservationId.of(UUID.fromString("09518c66-ff5e-4596-9049-74dfbdf6f6db")),
                Money.of(BigDecimal.valueOf(40.00), SupportedCurrency.EUR),
                4L,
                Instant.parse("2025-11-16T15:00:00Z")
        );
        EventStore accountClosedEvent = new EventStore();
        accountClosedEvent.setEventType("AccountClosed");
        accountClosedEvent.setAccountId(accountId);
        accountClosedEvent.setVersion(55L);
        accountClosedEvent.setPayload(JSONB.valueOf("""
                {
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """));
        AccountClosed accountClosed = new AccountClosed(
                AccountId.of(accountId),
                Instant.parse("2025-11-16T15:00:00Z"),
                55L);

        EventStore fundsCreditedEvent = new EventStore();
        fundsCreditedEvent.setEventType("FundsCredited");
        fundsCreditedEvent.setAccountId(accountId);
        fundsCreditedEvent.setVersion(4L);
        fundsCreditedEvent.setPayload(JSONB.valueOf("""
                {
                    "transactionId": "93c1fbc0-3d93-43f2-a127-b3c5d1c7722c",
                    "currency": "USD",
                    "amount": 500.00,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """));
        FundsCredited fundsCredited = new FundsCredited(
                TransactionId.of(UUID.fromString("93c1fbc0-3d93-43f2-a127-b3c5d1c7722c")),
                AccountId.of(accountId),
                Instant.parse("2025-11-16T15:00:00Z"),
                4L,
                Money.of(BigDecimal.valueOf(500L), SupportedCurrency.USD)
        );
        EventStore fundsDebitedEvent = new EventStore();
        fundsDebitedEvent.setEventType("FundsDebited");
        fundsDebitedEvent.setAccountId(accountId);
        fundsDebitedEvent.setVersion(6L);
        fundsDebitedEvent.setPayload(JSONB.valueOf("""
                {
                    "transactionId": "bdbe57ef-6930-4502-a916-e77d978e1f76",
                    "currency": "CHF",
                    "amount": 20.00,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """));
        FundsDebited fundsDebited = new FundsDebited(
                TransactionId.of(UUID.fromString("bdbe57ef-6930-4502-a916-e77d978e1f76")),
                AccountId.of(accountId),
                Instant.parse("2025-11-16T15:00:00Z"),
                6L,
                Money.of(BigDecimal.valueOf(20L), SupportedCurrency.CHF)
        );
        EventStore fundsReservedEvent = new EventStore();
        fundsReservedEvent.setEventType("FundsReserved");
        fundsReservedEvent.setAccountId(accountId);
        fundsReservedEvent.setVersion(12L);
        fundsReservedEvent.setPayload(JSONB.valueOf("""
                {
                    "reservationId": "09518c66-ff5e-4596-9049-74dfbdf6f6db",
                    "currency": "GBP",
                    "amount": 40.10,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """));
        FundsReserved fundsReserved = new FundsReserved(
                AccountId.of(accountId),
                Instant.parse("2025-11-16T15:00:00Z"),
                12L,
                ReservationId.of(UUID.fromString("09518c66-ff5e-4596-9049-74dfbdf6f6db")),
                Money.of(BigDecimal.valueOf(40.10), SupportedCurrency.GBP)
        );
        EventStore fundsReservationCancelledEvent = new EventStore();
        fundsReservationCancelledEvent.setEventType("ReservationCancelled");
        fundsReservationCancelledEvent.setAccountId(accountId);
        fundsReservationCancelledEvent.setVersion(13L);
        fundsReservationCancelledEvent.setPayload(JSONB.valueOf("""
                {
                    "reservationId": "09518c66-ff5e-4596-9049-74dfbdf6f6db",
                    "currency": "GBP",
                    "amount": 40.10,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """));
        ReservationCancelled reservationCancelled = new ReservationCancelled(
                AccountId.of(accountId),
                Instant.parse("2025-11-16T15:00:00Z"),
                13L,
                ReservationId.of(UUID.fromString("09518c66-ff5e-4596-9049-74dfbdf6f6db")),
                Money.of(BigDecimal.valueOf(40.10), SupportedCurrency.GBP)
        );
        return Stream.of(
                Arguments.of(accountOpenedEvent, accountOpened),
                Arguments.of(reservationCapturedEvent, reservationCaptured),
                Arguments.of(accountClosedEvent, accountClosed),
                Arguments.of(fundsCreditedEvent, fundsCredited),
                Arguments.of(fundsDebitedEvent, fundsDebited),
                Arguments.of(fundsReservedEvent, fundsReserved),
                Arguments.of(fundsReservationCancelledEvent, reservationCancelled)
        );
    }

    @ParameterizedTest
    @MethodSource("eventSourceAndExpectedEvent")
    void deserialize_account_event(EventStore eventStore, AccountEvent expectedEvent) {
        // Act
        AccountEvent deserializedEvent = mapper.apply(eventStore);

        // Assert
        assertThat(deserializedEvent).isEqualTo(expectedEvent);
    }

}