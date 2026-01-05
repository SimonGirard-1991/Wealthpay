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

class AccountEventSerializerTest {

    AccountEventSerializer accountEventSerializer = new AccountEventSerializer(new ObjectMapper());

    public static Stream<Arguments> accountEventProvider() {
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.valueOf(10), usd);
        Instant occurredAt = Instant.parse("2025-11-16T15:00:00Z");
        AccountOpened accountOpened = new AccountOpened(
                AccountId.newId(),
                occurredAt,
                1L,
                usd,
                initialBalance
        );
        JSONB payloadAccountOpened = JSONB.valueOf("""
                {
                    "currency": "USD",
                    "initialBalance": 10.00,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """);
        ReservationId reservationId = ReservationId.of(UUID.fromString("09518c66-ff5e-4596-9049-74dfbdf6f6db"));
        ReservationCaptured reservationCaptured = new ReservationCaptured(
                AccountId.newId(),
                reservationId,
                Money.of(BigDecimal.valueOf(40), SupportedCurrency.EUR),
                3L,
                occurredAt
        );
        JSONB payloadReservationCaptured = JSONB.valueOf("""
                {
                    "reservationId": "09518c66-ff5e-4596-9049-74dfbdf6f6db",
                    "currency": "EUR",
                    "amount": 40.00,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """);
        AccountClosed accountClosed = new AccountClosed(AccountId.newId(),
                occurredAt,
                100L);
        JSONB payloadAccountClosed = JSONB.valueOf("""
                {
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """);
        FundsCredited fundsCredited = new FundsCredited(
                TransactionId.of(UUID.fromString("93c1fbc0-3d93-43f2-a127-b3c5d1c7722c")),
                AccountId.newId(),
                occurredAt,
                2L,
                Money.of(BigDecimal.valueOf(500L), SupportedCurrency.USD)
        );
        JSONB payloadFundsCredited = JSONB.valueOf("""
                {
                    "transactionId": "93c1fbc0-3d93-43f2-a127-b3c5d1c7722c",
                    "currency": "USD",
                    "amount": 500.00,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """);
        FundsDebited fundsDebited = new FundsDebited(
                TransactionId.of(UUID.fromString("bdbe57ef-6930-4502-a916-e77d978e1f76")),
                AccountId.newId(),
                occurredAt,
                2L,
                Money.of(BigDecimal.valueOf(20L), SupportedCurrency.CHF)
        );
        JSONB payloadFundsDebited = JSONB.valueOf("""
                {
                    "transactionId": "bdbe57ef-6930-4502-a916-e77d978e1f76",
                    "currency": "CHF",
                    "amount": 20.00,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """);
        FundsReserved fundsReserved = new FundsReserved(
                AccountId.newId(),
                occurredAt,
                2L,
                reservationId,
                Money.of(BigDecimal.valueOf(40.10), SupportedCurrency.GBP)
        );
        JSONB payloadFundsReserved = JSONB.valueOf("""
                {
                    "reservationId": "09518c66-ff5e-4596-9049-74dfbdf6f6db",
                    "currency": "GBP",
                    "amount": 40.10,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """);
        ReservationCancelled reservationCancelled = new ReservationCancelled(
                AccountId.newId(),
                occurredAt,
                2L,
                reservationId,
                Money.of(BigDecimal.valueOf(40.10), SupportedCurrency.GBP)
        );
        JSONB payloadReservationCancelled = JSONB.valueOf("""
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
                Arguments.of(reservationCancelled, payloadReservationCancelled)
        );
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