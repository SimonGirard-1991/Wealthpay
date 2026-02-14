package org.girardsimon.wealthpay.account.infrastructure.consumer.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCancelled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.exception.UnsupportedCurrencyException;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.EventId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

class AccountEventDeserializerTest {

  AccountEventDeserializer accountEventDeserializer =
      new AccountEventDeserializer(new ObjectMapper());

  private static ConsumerRecord<String, Object> buildConsumerRecord(
      AccountId accountId,
      EventId eventId,
      String eventType,
      Instant occurredAt,
      long aggregateVersion,
      String payloadJson) {

    RecordHeaders headers = new RecordHeaders();
    headers.add("id", eventId.id().toString().getBytes(StandardCharsets.UTF_8));
    headers.add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
    headers.add("occurredAt", occurredAt.toString().getBytes(StandardCharsets.UTF_8));
    headers.add(
        "aggregateVersion", String.valueOf(aggregateVersion).getBytes(StandardCharsets.UTF_8));

    return new ConsumerRecord<>(
        "wealthpay.AccountEvent",
        0,
        0L,
        ConsumerRecord.NO_TIMESTAMP,
        TimestampType.NO_TIMESTAMP_TYPE,
        0,
        0,
        accountId.id().toString(),
        payloadJson,
        headers,
        Optional.empty());
  }

  static Stream<Arguments> consumerRecordAndExpectedEvent() {
    AccountId accountId = AccountId.newId();
    String openAccountPayload =
        """
        {"currency":"USD","initialBalance":100.00,"occurredAt":"2025-11-16T15:00:00Z"}
        """;
    EventId openAccountEventId = EventId.newId();
    Instant occuredAt = Instant.parse("2025-11-16T15:00:00Z");
    ConsumerRecord<String, Object> accountOpened =
        buildConsumerRecord(
            accountId, openAccountEventId, "AccountOpened", occuredAt, 1L, openAccountPayload);
    AccountEventMeta meta1 = AccountEventMeta.of(openAccountEventId, accountId, occuredAt, 1L);
    AccountOpened accountOpenedEvent =
        new AccountOpened(
            meta1,
            SupportedCurrency.USD,
            Money.of(new BigDecimal("100.00"), SupportedCurrency.USD));
    String fundsCreditedPayload =
        """
        {"transactionId":"6a19786d-7b2a-466a-b0c7-e82997b0d979","currency":"SGD","amount":50.0,"occurredAt":"2025-11-16T15:00:00Z"}
        """;
    EventId fundsCreditedEventId = EventId.newId();
    ConsumerRecord<String, Object> fundsCredited =
        buildConsumerRecord(
            accountId, fundsCreditedEventId, "FundsCredited", occuredAt, 2L, fundsCreditedPayload);
    AccountEventMeta meta2 = AccountEventMeta.of(fundsCreditedEventId, accountId, occuredAt, 2L);
    TransactionId fundsCreditedTransactionId =
        TransactionId.of(UUID.fromString("6a19786d-7b2a-466a-b0c7-e82997b0d979"));
    FundsCredited fundsCreditedEvent =
        new FundsCredited(
            meta2,
            fundsCreditedTransactionId,
            Money.of(new BigDecimal("50.00"), SupportedCurrency.SGD));

    String fundsDebitedPayload =
        """
        {"transactionId":"5235b2a6-0e18-4f6e-a2b3-a3753256913c","currency":"EUR","amount":25.0,"occurredAt":"2025-11-16T15:00:00Z"}
        """;
    EventId fundsDebitedEventId = EventId.newId();
    ConsumerRecord<String, Object> fundsDebited =
        buildConsumerRecord(
            accountId, fundsDebitedEventId, "FundsDebited", occuredAt, 3L, fundsDebitedPayload);
    AccountEventMeta meta3 = AccountEventMeta.of(fundsDebitedEventId, accountId, occuredAt, 3L);
    TransactionId fundsDebitedTransactionId =
        TransactionId.of(UUID.fromString("5235b2a6-0e18-4f6e-a2b3-a3753256913c"));
    FundsDebited fundsDebitedEvent =
        new FundsDebited(
            meta3,
            fundsDebitedTransactionId,
            Money.of(new BigDecimal("25.00"), SupportedCurrency.EUR));

    String fundsReservedPayload =
        """
        {"reservationId":"a8129cdf-801a-430f-9b77-06d2c5377304","currency":"GBP","amount":75.0,"occurredAt":"2025-11-16T15:00:00Z"}
        """;
    EventId fundsReservedEventId = EventId.newId();
    ConsumerRecord<String, Object> fundsReserved =
        buildConsumerRecord(
            accountId, fundsReservedEventId, "FundsReserved", occuredAt, 4L, fundsReservedPayload);
    AccountEventMeta meta4 = AccountEventMeta.of(fundsReservedEventId, accountId, occuredAt, 4L);
    ReservationId fundsReservedReservationId =
        ReservationId.of(UUID.fromString("a8129cdf-801a-430f-9b77-06d2c5377304"));
    FundsReserved fundsReservedEvent =
        new FundsReserved(
            meta4,
            fundsReservedReservationId,
            Money.of(new BigDecimal("75.00"), SupportedCurrency.GBP));

    String reservationCancelledPayload =
        """
        {"reservationId":"eb5739f5-d653-4716-8731-07136a5f9891","currency":"JPY","amount":100.0,"occurredAt":"2025-11-16T15:00:00Z"}
        """;
    EventId reservationCancelledEventId = EventId.newId();
    ConsumerRecord<String, Object> reservationCancelled =
        buildConsumerRecord(
            accountId,
            reservationCancelledEventId,
            "ReservationCancelled",
            occuredAt,
            5L,
            reservationCancelledPayload);
    AccountEventMeta meta5 =
        AccountEventMeta.of(reservationCancelledEventId, accountId, occuredAt, 5L);
    ReservationId reservationCancelledReservationId =
        ReservationId.of(UUID.fromString("eb5739f5-d653-4716-8731-07136a5f9891"));
    ReservationCancelled reservationCancelledEvent =
        new ReservationCancelled(
            meta5,
            reservationCancelledReservationId,
            Money.of(new BigDecimal("100.00"), SupportedCurrency.JPY));

    String reservationCapturedPayload =
        """
        {"reservationId":"b28ebaea-ffa5-4810-9bda-1ceef89135ef","currency":"CAD","amount":150.0,"occurredAt":"2025-11-16T15:00:00Z"}
        """;
    EventId reservationCapturedEventId = EventId.newId();
    ConsumerRecord<String, Object> reservationCaptured =
        buildConsumerRecord(
            accountId,
            reservationCapturedEventId,
            "ReservationCaptured",
            occuredAt,
            6L,
            reservationCapturedPayload);
    AccountEventMeta meta6 =
        AccountEventMeta.of(reservationCapturedEventId, accountId, occuredAt, 6L);
    ReservationId reservationCapturedReservationId =
        ReservationId.of(UUID.fromString("b28ebaea-ffa5-4810-9bda-1ceef89135ef"));
    ReservationCaptured reservationCapturedEvent =
        new ReservationCaptured(
            meta6,
            reservationCapturedReservationId,
            Money.of(new BigDecimal("150.00"), SupportedCurrency.CAD));

    String accountClosedPayload = "{}";
    EventId accountClosedEventId = EventId.newId();
    ConsumerRecord<String, Object> accountClosed =
        buildConsumerRecord(
            accountId, accountClosedEventId, "AccountClosed", occuredAt, 7L, accountClosedPayload);
    AccountEventMeta meta7 = AccountEventMeta.of(accountClosedEventId, accountId, occuredAt, 7L);
    AccountClosed accountClosedEvent = new AccountClosed(meta7);

    return Stream.of(
        Arguments.of(accountOpened, accountOpenedEvent),
        Arguments.of(fundsCredited, fundsCreditedEvent),
        Arguments.of(fundsDebited, fundsDebitedEvent),
        Arguments.of(fundsReserved, fundsReservedEvent),
        Arguments.of(reservationCancelled, reservationCancelledEvent),
        Arguments.of(reservationCaptured, reservationCapturedEvent),
        Arguments.of(accountClosed, accountClosedEvent));
  }

  static Stream<Arguments> consumerRecordsWithMissingHeaders() {
    AccountId accountId = AccountId.newId();
    String payload =
        """
        {"currency":"USD","initialBalance":100.00,"occurredAt":"2025-11-16T15:00:00Z"}
        """;

    // Missing id header
    RecordHeaders headersWithoutId = new RecordHeaders();
    headersWithoutId.add("eventType", "AccountOpened".getBytes(StandardCharsets.UTF_8));
    headersWithoutId.add("occurredAt", "2025-11-16T15:00:00Z".getBytes(StandardCharsets.UTF_8));
    headersWithoutId.add("aggregateVersion", "1".getBytes(StandardCharsets.UTF_8));
    ConsumerRecord<String, Object> recordWithoutId =
        new ConsumerRecord<>(
            "wealthpay.AccountEvent",
            0,
            0L,
            ConsumerRecord.NO_TIMESTAMP,
            TimestampType.NO_TIMESTAMP_TYPE,
            0,
            0,
            accountId.id().toString(),
            payload,
            headersWithoutId,
            Optional.empty());

    // Missing eventType header
    RecordHeaders headersWithoutEventType = new RecordHeaders();
    headersWithoutEventType.add(
        "id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    headersWithoutEventType.add(
        "occurredAt", "2025-11-16T15:00:00Z".getBytes(StandardCharsets.UTF_8));
    headersWithoutEventType.add("aggregateVersion", "1".getBytes(StandardCharsets.UTF_8));
    ConsumerRecord<String, Object> recordWithoutEventType =
        new ConsumerRecord<>(
            "wealthpay.AccountEvent",
            0,
            0L,
            ConsumerRecord.NO_TIMESTAMP,
            TimestampType.NO_TIMESTAMP_TYPE,
            0,
            0,
            accountId.id().toString(),
            payload,
            headersWithoutEventType,
            Optional.empty());

    // Missing occurredAt header
    RecordHeaders headersWithoutOccurredAt = new RecordHeaders();
    headersWithoutOccurredAt.add(
        "id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    headersWithoutOccurredAt.add("eventType", "AccountOpened".getBytes(StandardCharsets.UTF_8));
    headersWithoutOccurredAt.add("aggregateVersion", "1".getBytes(StandardCharsets.UTF_8));
    ConsumerRecord<String, Object> recordWithoutOccurredAt =
        new ConsumerRecord<>(
            "wealthpay.AccountEvent",
            0,
            0L,
            ConsumerRecord.NO_TIMESTAMP,
            TimestampType.NO_TIMESTAMP_TYPE,
            0,
            0,
            accountId.id().toString(),
            payload,
            headersWithoutOccurredAt,
            Optional.empty());

    // Missing aggregateVersion header
    RecordHeaders headersWithoutAggregateVersion = new RecordHeaders();
    headersWithoutAggregateVersion.add(
        "id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    headersWithoutAggregateVersion.add(
        "eventType", "AccountOpened".getBytes(StandardCharsets.UTF_8));
    headersWithoutAggregateVersion.add(
        "occurredAt", "2025-11-16T15:00:00Z".getBytes(StandardCharsets.UTF_8));
    ConsumerRecord<String, Object> recordWithoutAggregateVersion =
        new ConsumerRecord<>(
            "wealthpay.AccountEvent",
            0,
            0L,
            ConsumerRecord.NO_TIMESTAMP,
            TimestampType.NO_TIMESTAMP_TYPE,
            0,
            0,
            accountId.id().toString(),
            payload,
            headersWithoutAggregateVersion,
            Optional.empty());

    return Stream.of(
        Arguments.of(recordWithoutId),
        Arguments.of(recordWithoutEventType),
        Arguments.of(recordWithoutOccurredAt),
        Arguments.of(recordWithoutAggregateVersion));
  }

  static Stream<Arguments> consumerRecordsWithMissingFields() {
    AccountId accountId = AccountId.newId();
    EventId eventId = EventId.newId();
    Instant occurredAt = Instant.parse("2025-11-16T15:00:00Z");

    // AccountOpened missing currency
    String accountOpenedMissingCurrency =
        """
        {"initialBalance":100.00,"occurredAt":"2025-11-16T15:00:00Z"}
        """;
    ConsumerRecord<String, Object> recordAccountOpenedMissingCurrency =
        buildConsumerRecord(
            accountId, eventId, "AccountOpened", occurredAt, 1L, accountOpenedMissingCurrency);

    // AccountOpened missing initialBalance
    String accountOpenedMissingBalance =
        """
        {"currency":"USD","occurredAt":"2025-11-16T15:00:00Z"}
        """;
    ConsumerRecord<String, Object> recordAccountOpenedMissingBalance =
        buildConsumerRecord(
            accountId, eventId, "AccountOpened", occurredAt, 1L, accountOpenedMissingBalance);

    // FundsCredited missing transactionId
    String fundsCreditedMissingTransactionId =
        """
        {"currency":"USD","amount":50.0,"occurredAt":"2025-11-16T15:00:00Z"}
        """;
    ConsumerRecord<String, Object> recordFundsCreditedMissingTransactionId =
        buildConsumerRecord(
            accountId, eventId, "FundsCredited", occurredAt, 1L, fundsCreditedMissingTransactionId);

    // FundsCredited missing amount
    String fundsCreditedMissingAmount =
        """
        {"transactionId":"6a19786d-7b2a-466a-b0c7-e82997b0d979","currency":"USD","occurredAt":"2025-11-16T15:00:00Z"}
        """;
    ConsumerRecord<String, Object> recordFundsCreditedMissingAmount =
        buildConsumerRecord(
            accountId, eventId, "FundsCredited", occurredAt, 1L, fundsCreditedMissingAmount);

    // FundsReserved missing reservationId
    String fundsReservedMissingReservationId =
        """
        {"currency":"USD","amount":75.0,"occurredAt":"2025-11-16T15:00:00Z"}
        """;
    ConsumerRecord<String, Object> recordFundsReservedMissingReservationId =
        buildConsumerRecord(
            accountId, eventId, "FundsReserved", occurredAt, 1L, fundsReservedMissingReservationId);

    return Stream.of(
        Arguments.of(recordAccountOpenedMissingCurrency),
        Arguments.of(recordAccountOpenedMissingBalance),
        Arguments.of(recordFundsCreditedMissingTransactionId),
        Arguments.of(recordFundsCreditedMissingAmount),
        Arguments.of(recordFundsReservedMissingReservationId));
  }

  static Stream<Arguments> consumerRecordsWithInvalidUUIDs() {
    AccountId accountId = AccountId.newId();
    EventId eventId = EventId.newId();
    Instant occurredAt = Instant.parse("2025-11-16T15:00:00Z");

    // Invalid transactionId UUID
    String invalidTransactionId =
        """
        {"transactionId":"invalid-uuid","currency":"USD","amount":50.0,"occurredAt":"2025-11-16T15:00:00Z"}
        """;
    ConsumerRecord<String, Object> recordWithInvalidTransactionId =
        buildConsumerRecord(
            accountId, eventId, "FundsCredited", occurredAt, 1L, invalidTransactionId);

    // Invalid reservationId UUID
    String invalidReservationId =
        """
        {"reservationId":"not-a-valid-uuid","currency":"USD","amount":75.0,"occurredAt":"2025-11-16T15:00:00Z"}
        """;
    ConsumerRecord<String, Object> recordWithInvalidReservationId =
        buildConsumerRecord(
            accountId, eventId, "FundsReserved", occurredAt, 1L, invalidReservationId);

    // Invalid account key UUID
    RecordHeaders headers = new RecordHeaders();
    headers.add("id", eventId.id().toString().getBytes(StandardCharsets.UTF_8));
    headers.add("eventType", "AccountOpened".getBytes(StandardCharsets.UTF_8));
    headers.add("occurredAt", occurredAt.toString().getBytes(StandardCharsets.UTF_8));
    headers.add("aggregateVersion", "1".getBytes(StandardCharsets.UTF_8));
    ConsumerRecord<String, Object> recordWithInvalidAccountId =
        new ConsumerRecord<>(
            "wealthpay.AccountEvent",
            0,
            0L,
            ConsumerRecord.NO_TIMESTAMP,
            TimestampType.NO_TIMESTAMP_TYPE,
            0,
            0,
            "invalid-account-uuid",
            "{\"currency\":\"USD\",\"initialBalance\":100.00}",
            headers,
            Optional.empty());

    return Stream.of(
        Arguments.of(recordWithInvalidTransactionId),
        Arguments.of(recordWithInvalidReservationId),
        Arguments.of(recordWithInvalidAccountId));
  }

  @ParameterizedTest
  @MethodSource("consumerRecordAndExpectedEvent")
  void apply_should_deserialize_consumer_record_as_expected(
      ConsumerRecord<String, Object> consumerRecord, AccountEvent expectedAccountEvent) {
    // Act
    AccountEvent accountEvent = accountEventDeserializer.apply(consumerRecord);

    // Assert
    assertThat(accountEvent).isEqualTo(expectedAccountEvent);
  }

  @ParameterizedTest
  @MethodSource("consumerRecordsWithMissingHeaders")
  void apply_should_throw_IllegalArgumentException_when_required_header_is_missing(
      ConsumerRecord<String, Object> consumerRecord) {

    // Act ... Assert
    assertThatThrownBy(() -> accountEventDeserializer.apply(consumerRecord))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("consumerRecordsWithMissingFields")
  void apply_should_throw_IllegalStateException_when_required_field_is_missing(
      ConsumerRecord<String, Object> consumerRecord) {
    // Act ... Assert
    assertThatThrownBy(() -> accountEventDeserializer.apply(consumerRecord))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @MethodSource("consumerRecordsWithInvalidUUIDs")
  void apply_should_throw_IllegalArgumentException_when_UUID_is_invalid(
      ConsumerRecord<String, Object> consumerRecord) {

    // Act ... Assert
    assertThatThrownBy(() -> accountEventDeserializer.apply(consumerRecord))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void apply_should_throw_UnsupportedCurrencyException_when_currency_is_invalid() {
    // Arrange
    AccountId accountId = AccountId.newId();
    EventId eventId = EventId.newId();
    Instant occurredAt = Instant.parse("2026-02-13T22:00:00Z");
    String payload =
        """
        {"transactionId":"6a19786d-7b2a-466a-b0c7-e82997b0d979","currency":"XYZ","amount":50.0,"occurredAt":"2026-02-13T22:00:00Z"}
        """;
    ConsumerRecord<String, Object> recordWithInvalidCurrency =
        buildConsumerRecord(accountId, eventId, "FundsCredited", occurredAt, 1L, payload);

    // Act ... Assert
    assertThatThrownBy(() -> accountEventDeserializer.apply(recordWithInvalidCurrency))
        .isInstanceOf(UnsupportedCurrencyException.class);
  }

  @Test
  void apply_should_throw_IllegalArgumentException_when_event_type_is_invalid() {
    // Arrange
    AccountId accountId = AccountId.newId();
    EventId eventId = EventId.newId();
    Instant occurredAt = Instant.parse("2025-11-16T15:00:00Z");
    String payload =
        """
      {"currency":"USD","initialBalance":100.00,"occurredAt":"2025-11-16T15:00:00Z"}
      """;
    ConsumerRecord<String, Object> recordWithInvalidEventType =
        buildConsumerRecord(accountId, eventId, "InvalidEventType", occurredAt, 1L, payload);

    // Act ... Assert
    assertThatThrownBy(() -> accountEventDeserializer.apply(recordWithInvalidEventType))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
