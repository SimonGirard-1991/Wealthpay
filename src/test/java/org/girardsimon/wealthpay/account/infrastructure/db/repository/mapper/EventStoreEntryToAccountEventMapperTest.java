package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCanceled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.EventId;
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

class EventStoreEntryToAccountEventMapperTest {

  EventStoreEntryToAccountEventMapper mapper =
      new EventStoreEntryToAccountEventMapper(new ObjectMapper());

  public static Stream<Arguments> eventSourceAndExpectedEvent() {
    EventStore accountOpenedEvent = new EventStore();
    accountOpenedEvent.setEventType("AccountOpened");
    UUID eventId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    accountOpenedEvent.setAccountId(accountId);
    accountOpenedEvent.setEventId(eventId);
    accountOpenedEvent.setVersion(1L);
    accountOpenedEvent.setPayload(
        JSONB.valueOf(
            """
            {
                "currency": "USD",
                "initialBalance": 10.00,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """));
    AccountEventMeta metaOpened =
        AccountEventMeta.of(
            EventId.of(eventId),
            AccountId.of(accountId),
            Instant.parse("2025-11-16T15:00:00Z"),
            1L);
    AccountOpened accountOpened =
        new AccountOpened(
            metaOpened,
            SupportedCurrency.USD,
            Money.of(BigDecimal.valueOf(10.00), SupportedCurrency.USD));
    UUID reservationCaptureEventId = UUID.randomUUID();
    EventStore reservationCapturedEvent = new EventStore();
    reservationCapturedEvent.setEventType("ReservationCaptured");
    reservationCapturedEvent.setEventId(reservationCaptureEventId);
    reservationCapturedEvent.setAccountId(accountId);
    reservationCapturedEvent.setVersion(4L);
    reservationCapturedEvent.setPayload(
        JSONB.valueOf(
            """
            {
                "reservationId": "09518c66-ff5e-4596-9049-74dfbdf6f6db",
                "currency": "EUR",
                "amount": 40.00,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """));
    AccountEventMeta metaCaptured =
        AccountEventMeta.of(
            EventId.of(reservationCaptureEventId),
            AccountId.of(accountId),
            Instant.parse("2025-11-16T15:00:00Z"),
            4L);
    ReservationCaptured reservationCaptured =
        new ReservationCaptured(
            metaCaptured,
            ReservationId.of(UUID.fromString("09518c66-ff5e-4596-9049-74dfbdf6f6db")),
            Money.of(BigDecimal.valueOf(40.00), SupportedCurrency.EUR));
    UUID accountClosedEventId = UUID.randomUUID();
    EventStore accountClosedEvent = new EventStore();
    accountClosedEvent.setEventType("AccountClosed");
    accountClosedEvent.setEventId(accountClosedEventId);
    accountClosedEvent.setAccountId(accountId);
    accountClosedEvent.setVersion(55L);
    accountClosedEvent.setPayload(
        JSONB.valueOf(
            """
            {
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """));
    AccountEventMeta metaClosed =
        AccountEventMeta.of(
            EventId.of(accountClosedEventId),
            AccountId.of(accountId),
            Instant.parse("2025-11-16T15:00:00Z"),
            55L);
    AccountClosed accountClosed = new AccountClosed(metaClosed);
    UUID fundsCreditedEventId = UUID.randomUUID();
    EventStore fundsCreditedEvent = new EventStore();
    fundsCreditedEvent.setEventType("FundsCredited");
    fundsCreditedEvent.setEventId(fundsCreditedEventId);
    fundsCreditedEvent.setAccountId(accountId);
    fundsCreditedEvent.setVersion(4L);
    fundsCreditedEvent.setPayload(
        JSONB.valueOf(
            """
            {
                "transactionId": "93c1fbc0-3d93-43f2-a127-b3c5d1c7722c",
                "currency": "USD",
                "amount": 500.00,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """));
    AccountEventMeta metaCredited =
        AccountEventMeta.of(
            EventId.of(fundsCreditedEventId),
            AccountId.of(accountId),
            Instant.parse("2025-11-16T15:00:00Z"),
            4L);
    FundsCredited fundsCredited =
        new FundsCredited(
            metaCredited,
            TransactionId.of(UUID.fromString("93c1fbc0-3d93-43f2-a127-b3c5d1c7722c")),
            Money.of(BigDecimal.valueOf(500L), SupportedCurrency.USD));
    UUID fundsDebitedEventId = UUID.randomUUID();
    EventStore fundsDebitedEvent = new EventStore();
    fundsDebitedEvent.setEventType("FundsDebited");
    fundsDebitedEvent.setEventId(fundsDebitedEventId);
    fundsDebitedEvent.setAccountId(accountId);
    fundsDebitedEvent.setVersion(6L);
    fundsDebitedEvent.setPayload(
        JSONB.valueOf(
            """
            {
                "transactionId": "bdbe57ef-6930-4502-a916-e77d978e1f76",
                "currency": "CHF",
                "amount": 20.00,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """));
    AccountEventMeta metaDebited =
        AccountEventMeta.of(
            EventId.of(fundsDebitedEventId),
            AccountId.of(accountId),
            Instant.parse("2025-11-16T15:00:00Z"),
            6L);
    FundsDebited fundsDebited =
        new FundsDebited(
            metaDebited,
            TransactionId.of(UUID.fromString("bdbe57ef-6930-4502-a916-e77d978e1f76")),
            Money.of(BigDecimal.valueOf(20L), SupportedCurrency.CHF));
    UUID fundsReservedEventId = UUID.randomUUID();
    EventStore fundsReservedEvent = new EventStore();
    fundsReservedEvent.setEventType("FundsReserved");
    fundsReservedEvent.setEventId(fundsReservedEventId);
    fundsReservedEvent.setAccountId(accountId);
    fundsReservedEvent.setVersion(12L);
    fundsReservedEvent.setPayload(
        JSONB.valueOf(
            """
            {
                "transactionId": "48df9a88-50f0-47c6-96fd-f2afff2abf8d",
                "reservationId": "09518c66-ff5e-4596-9049-74dfbdf6f6db",
                "currency": "GBP",
                "amount": 40.10,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """));
    AccountEventMeta metaReserved =
        AccountEventMeta.of(
            EventId.of(fundsReservedEventId),
            AccountId.of(accountId),
            Instant.parse("2025-11-16T15:00:00Z"),
            12L);
    FundsReserved fundsReserved =
        new FundsReserved(
            metaReserved,
            TransactionId.of(UUID.fromString("48df9a88-50f0-47c6-96fd-f2afff2abf8d")),
            ReservationId.of(UUID.fromString("09518c66-ff5e-4596-9049-74dfbdf6f6db")),
            Money.of(BigDecimal.valueOf(40.10), SupportedCurrency.GBP));
    UUID reservationCanceledEventId = UUID.randomUUID();
    EventStore fundsReservationCanceledEvent = new EventStore();
    fundsReservationCanceledEvent.setEventType("ReservationCanceled");
    fundsReservationCanceledEvent.setEventId(reservationCanceledEventId);
    fundsReservationCanceledEvent.setAccountId(accountId);
    fundsReservationCanceledEvent.setVersion(13L);
    fundsReservationCanceledEvent.setPayload(
        JSONB.valueOf(
            """
            {
                "reservationId": "09518c66-ff5e-4596-9049-74dfbdf6f6db",
                "currency": "GBP",
                "amount": 40.10,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """));
    AccountEventMeta metaReservationCanceled =
        AccountEventMeta.of(
            EventId.of(reservationCanceledEventId),
            AccountId.of(accountId),
            Instant.parse("2025-11-16T15:00:00Z"),
            13L);
    ReservationCanceled reservationCanceled =
        new ReservationCanceled(
            metaReservationCanceled,
            ReservationId.of(UUID.fromString("09518c66-ff5e-4596-9049-74dfbdf6f6db")),
            Money.of(BigDecimal.valueOf(40.10), SupportedCurrency.GBP));
    return Stream.of(
        Arguments.of(accountOpenedEvent, accountOpened),
        Arguments.of(reservationCapturedEvent, reservationCaptured),
        Arguments.of(accountClosedEvent, accountClosed),
        Arguments.of(fundsCreditedEvent, fundsCredited),
        Arguments.of(fundsDebitedEvent, fundsDebited),
        Arguments.of(fundsReservedEvent, fundsReserved),
        Arguments.of(fundsReservationCanceledEvent, reservationCanceled));
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
