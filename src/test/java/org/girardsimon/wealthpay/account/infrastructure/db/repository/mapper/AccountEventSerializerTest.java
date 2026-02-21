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
import org.jooq.JSONB;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

class AccountEventSerializerTest {

  AccountEventSerializer accountEventSerializer = new AccountEventSerializer(new ObjectMapper());

  public static Stream<Arguments> accountEventProvider() {
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.valueOf(10), usd);
    Instant occurredAt = Instant.parse("2025-11-16T15:00:00Z");
    AccountEventMeta metaOpened =
        AccountEventMeta.of(EventId.newId(), AccountId.newId(), occurredAt, 1L);
    AccountOpened accountOpened = new AccountOpened(metaOpened, usd, initialBalance);
    JSONB payloadAccountOpened =
        JSONB.valueOf(
            """
            {
                "currency": "USD",
                "initialBalance": 10.00,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """);
    ReservationId reservationId =
        ReservationId.of(UUID.fromString("09518c66-ff5e-4596-9049-74dfbdf6f6db"));
    AccountEventMeta metaCaptured =
        AccountEventMeta.of(EventId.newId(), AccountId.newId(), occurredAt, 3L);
    ReservationCaptured reservationCaptured =
        new ReservationCaptured(
            metaCaptured, reservationId, Money.of(BigDecimal.valueOf(40), SupportedCurrency.EUR));
    JSONB payloadReservationCaptured =
        JSONB.valueOf(
            """
            {
                "reservationId": "09518c66-ff5e-4596-9049-74dfbdf6f6db",
                "currency": "EUR",
                "amount": 40.00,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """);
    AccountEventMeta metaClosed =
        AccountEventMeta.of(EventId.newId(), AccountId.newId(), occurredAt, 10L);
    AccountClosed accountClosed = new AccountClosed(metaClosed);
    JSONB payloadAccountClosed =
        JSONB.valueOf(
            """
            {
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """);
    AccountEventMeta metaCredited =
        AccountEventMeta.of(EventId.newId(), AccountId.newId(), occurredAt, 2L);
    FundsCredited fundsCredited =
        new FundsCredited(
            metaCredited,
            TransactionId.of(UUID.fromString("93c1fbc0-3d93-43f2-a127-b3c5d1c7722c")),
            Money.of(BigDecimal.valueOf(500L), SupportedCurrency.USD));
    JSONB payloadFundsCredited =
        JSONB.valueOf(
            """
            {
                "transactionId": "93c1fbc0-3d93-43f2-a127-b3c5d1c7722c",
                "currency": "USD",
                "amount": 500.00,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """);
    AccountEventMeta metaDebited =
        AccountEventMeta.of(EventId.newId(), AccountId.newId(), occurredAt, 2L);
    FundsDebited fundsDebited =
        new FundsDebited(
            metaDebited,
            TransactionId.of(UUID.fromString("bdbe57ef-6930-4502-a916-e77d978e1f76")),
            Money.of(BigDecimal.valueOf(20L), SupportedCurrency.CHF));
    JSONB payloadFundsDebited =
        JSONB.valueOf(
            """
            {
                "transactionId": "bdbe57ef-6930-4502-a916-e77d978e1f76",
                "currency": "CHF",
                "amount": 20.00,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """);
    AccountEventMeta metaReserved =
        AccountEventMeta.of(EventId.newId(), AccountId.newId(), occurredAt, 2L);
    TransactionId transactionIdFundsReserved =
        TransactionId.of(UUID.fromString("48df9a88-50f0-47c6-96fd-f2afff2abf8d"));
    FundsReserved fundsReserved =
        new FundsReserved(
            metaReserved,
            transactionIdFundsReserved,
            reservationId,
            Money.of(BigDecimal.valueOf(40.10), SupportedCurrency.GBP));
    JSONB payloadFundsReserved =
        JSONB.valueOf(
            """
            {
                "transactionId": "48df9a88-50f0-47c6-96fd-f2afff2abf8d",
                "reservationId": "09518c66-ff5e-4596-9049-74dfbdf6f6db",
                "currency": "GBP",
                "amount": 40.10,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """);
    AccountEventMeta metaCanceled =
        AccountEventMeta.of(EventId.newId(), AccountId.newId(), occurredAt, 2L);
    ReservationCanceled reservationCanceled =
        new ReservationCanceled(
            metaCanceled,
            reservationId,
            Money.of(BigDecimal.valueOf(40.10), SupportedCurrency.GBP));
    JSONB payloadReservationCanceled =
        JSONB.valueOf(
            """
            {
                "reservationId": "09518c66-ff5e-4596-9049-74dfbdf6f6db",
                "currency": "GBP",
                "amount": 40.10,
                "occurredAt": "2025-11-16T15:00:00Z"
            }
            """);
    return Stream.of(
        Arguments.of(accountOpened, payloadAccountOpened),
        Arguments.of(reservationCaptured, payloadReservationCaptured),
        Arguments.of(accountClosed, payloadAccountClosed),
        Arguments.of(fundsCredited, payloadFundsCredited),
        Arguments.of(fundsDebited, payloadFundsDebited),
        Arguments.of(fundsReserved, payloadFundsReserved),
        Arguments.of(reservationCanceled, payloadReservationCanceled));
  }

  @ParameterizedTest
  @MethodSource("accountEventProvider")
  void serialize_account_event(AccountEvent accountEvent, JSONB expectedPayload) {
    // Act
    JSONB serializedAccountEvent = accountEventSerializer.apply(accountEvent);

    // Assert
    assertThat(serializedAccountEvent).isEqualTo(expectedPayload);
  }
}
