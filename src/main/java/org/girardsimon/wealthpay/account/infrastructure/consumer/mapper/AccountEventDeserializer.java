package org.girardsimon.wealthpay.account.infrastructure.consumer.mapper;

import static org.girardsimon.wealthpay.account.utils.MoneyDeserializerUtils.extractMoney;
import static org.girardsimon.wealthpay.shared.utils.MapperUtils.getRequiredField;
import static org.girardsimon.wealthpay.shared.utils.MapperUtils.headerAsString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCancelled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.EventId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.utils.AccountEventType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AccountEventDeserializer
    implements Function<ConsumerRecord<String, Object>, AccountEvent> {

  private static final String CURRENCY = "currency";
  private static final String RESERVATION_ID = "reservationId";
  private static final String INITIAL_BALANCE = "initialBalance";
  private static final String TRANSACTION_ID = "transactionId";

  private final ObjectMapper objectMapper;

  public AccountEventDeserializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  private static ReservationCaptured mapReservationCaptured(
      AccountEventMeta accountEventMeta, JsonNode payload) {
    String reservationId = getRequiredField(payload, RESERVATION_ID).asString();
    return new ReservationCaptured(
        accountEventMeta, ReservationId.of(UUID.fromString(reservationId)), extractMoney(payload));
  }

  private static ReservationCancelled mapReservationCancelled(
      AccountEventMeta accountEventMeta, JsonNode payload) {
    String reservationId = getRequiredField(payload, RESERVATION_ID).asString();
    return new ReservationCancelled(
        accountEventMeta, ReservationId.of(UUID.fromString(reservationId)), extractMoney(payload));
  }

  private static FundsReserved mapFundsReserved(
      AccountEventMeta accountEventMeta, JsonNode payload) {
    String reservationId = getRequiredField(payload, RESERVATION_ID).asString();
    Money money = extractMoney(payload);
    return new FundsReserved(
        accountEventMeta, ReservationId.of(UUID.fromString(reservationId)), money);
  }

  private static FundsDebited mapFundsDebited(AccountEventMeta accountEventMeta, JsonNode payload) {
    String transactionId = getRequiredField(payload, TRANSACTION_ID).asString();
    Money money = extractMoney(payload);
    return new FundsDebited(
        accountEventMeta, TransactionId.of(UUID.fromString(transactionId)), money);
  }

  private static FundsCredited mapFundsCredited(
      AccountEventMeta accountEventMeta, JsonNode payload) {
    String transactionId = getRequiredField(payload, TRANSACTION_ID).asString();
    Money money = extractMoney(payload);
    return new FundsCredited(
        accountEventMeta, TransactionId.of(UUID.fromString(transactionId)), money);
  }

  private static AccountOpened mapAccountOpened(
      AccountEventMeta accountEventMeta, JsonNode payload) {
    SupportedCurrency currency =
        SupportedCurrency.fromValue(getRequiredField(payload, CURRENCY).asString());
    BigDecimal amount = getRequiredField(payload, INITIAL_BALANCE).decimalValue();
    Money money = Money.of(amount, currency);
    return new AccountOpened(accountEventMeta, currency, money);
  }

  @Override
  public AccountEvent apply(ConsumerRecord<String, Object> consumerRecord) {

    EventId eventId = EventId.of(UUID.fromString(headerAsString(consumerRecord.headers(), "id")));
    AccountId accountId = AccountId.of(UUID.fromString(consumerRecord.key()));
    Instant createdAt = Instant.parse(headerAsString(consumerRecord.headers(), "occurredAt"));
    long aggregateVersion =
        Long.parseLong(headerAsString(consumerRecord.headers(), "aggregateVersion"));
    AccountEventMeta accountEventMeta =
        AccountEventMeta.of(eventId, accountId, createdAt, aggregateVersion);
    AccountEventType accountEventType =
        AccountEventType.from(headerAsString(consumerRecord.headers(), "eventType"));

    JsonNode payload = extractPayload(consumerRecord);

    return switch (accountEventType) {
      case ACCOUNT_OPENED -> mapAccountOpened(accountEventMeta, payload);
      case FUNDS_CREDITED -> mapFundsCredited(accountEventMeta, payload);
      case FUNDS_DEBITED -> mapFundsDebited(accountEventMeta, payload);
      case FUNDS_RESERVED -> mapFundsReserved(accountEventMeta, payload);
      case RESERVATION_CANCELLED -> mapReservationCancelled(accountEventMeta, payload);
      case RESERVATION_CAPTURED -> mapReservationCaptured(accountEventMeta, payload);
      case ACCOUNT_CLOSED -> new AccountClosed(accountEventMeta);
    };
  }

  private JsonNode extractPayload(ConsumerRecord<String, Object> consumerRecord) {
    Object value = consumerRecord.value();
    if (value == null) {
      throw new IllegalStateException("Value cannot be null");
    }
    String jsonString =
        switch (value) {
          case String s -> s;
          case org.apache.avro.util.Utf8 u -> u.toString();
          default -> throw new IllegalStateException("Unexpected value type: " + value.getClass());
        };

    return objectMapper.readTree(jsonString);
  }
}
