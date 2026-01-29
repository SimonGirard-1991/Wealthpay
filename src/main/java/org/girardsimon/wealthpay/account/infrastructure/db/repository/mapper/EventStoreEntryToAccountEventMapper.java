package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import static org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.MapperUtils.getRequiredField;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
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
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventStoreEntryToAccountEventMapper implements Function<EventStore, AccountEvent> {

  public static final String OCCURRED_AT = "occurredAt";
  public static final String AMOUNT = "amount";
  public static final String CURRENCY = "currency";
  public static final String RESERVATION_ID = "reservationId";
  public static final String INITIAL_BALANCE = "initialBalance";
  public static final String TRANSACTION_ID = "transactionId";

  private final ObjectMapper objectMapper;

  public EventStoreEntryToAccountEventMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  private static Money extractMoney(JsonNode root) {
    SupportedCurrency currency =
        SupportedCurrency.valueOf(getRequiredField(root, CURRENCY).asString());
    BigDecimal amount = getRequiredField(root, AMOUNT).decimalValue();
    return Money.of(amount, currency);
  }

  @Override
  public AccountEvent apply(EventStore eventStore) {
    String eventType = eventStore.getEventType();

    return switch (eventType) {
      case "AccountOpened" -> mapAccountOpened(eventStore);
      case "AccountClosed" -> mapAccountClosed(eventStore);
      case "ReservationCaptured" -> mapReservationCaptured(eventStore);
      case "FundsCredited" -> mapFundsCredited(eventStore);
      case "FundsDebited" -> mapFundsDebited(eventStore);
      case "FundsReserved" -> mapFundsReserved(eventStore);
      case "ReservationCancelled" -> mapReservationCancelled(eventStore);
      default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
    };
  }

  private ReservationCancelled mapReservationCancelled(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    String reservationId = getRequiredField(root, RESERVATION_ID).asString();

    return new ReservationCancelled(
        AccountId.of(eventStore.getAccountId()),
        Instant.parse(getRequiredField(root, OCCURRED_AT).asString()),
        eventStore.getVersion(),
        ReservationId.of(UUID.fromString(reservationId)),
        extractMoney(root));
  }

  private FundsReserved mapFundsReserved(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    String reservationId = getRequiredField(root, RESERVATION_ID).asString();

    return new FundsReserved(
        AccountId.of(eventStore.getAccountId()),
        Instant.parse(getRequiredField(root, OCCURRED_AT).asString()),
        eventStore.getVersion(),
        ReservationId.of(UUID.fromString(reservationId)),
        extractMoney(root));
  }

  private FundsDebited mapFundsDebited(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    String transactionId = root.get(TRANSACTION_ID).asString();

    return new FundsDebited(
        TransactionId.of(UUID.fromString(transactionId)),
        AccountId.of(eventStore.getAccountId()),
        Instant.parse(root.get(OCCURRED_AT).asString()),
        eventStore.getVersion(),
        extractMoney(root));
  }

  private FundsCredited mapFundsCredited(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    String transactionId = getRequiredField(root, TRANSACTION_ID).asString();

    return new FundsCredited(
        TransactionId.of(UUID.fromString(transactionId)),
        AccountId.of(eventStore.getAccountId()),
        Instant.parse(getRequiredField(root, OCCURRED_AT).asString()),
        eventStore.getVersion(),
        extractMoney(root));
  }

  private AccountClosed mapAccountClosed(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    return new AccountClosed(
        AccountId.of(eventStore.getAccountId()),
        Instant.parse(getRequiredField(root, OCCURRED_AT).asString()),
        eventStore.getVersion());
  }

  private ReservationCaptured mapReservationCaptured(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    String reservationId = getRequiredField(root, RESERVATION_ID).asString();

    return new ReservationCaptured(
        AccountId.of(eventStore.getAccountId()),
        ReservationId.of(UUID.fromString(reservationId)),
        extractMoney(root),
        eventStore.getVersion(),
        Instant.parse(getRequiredField(root, OCCURRED_AT).asString()));
  }

  private AccountOpened mapAccountOpened(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    SupportedCurrency currency =
        SupportedCurrency.valueOf(getRequiredField(root, CURRENCY).asString());
    BigDecimal amount = getRequiredField(root, INITIAL_BALANCE).decimalValue();

    return new AccountOpened(
        AccountId.of(eventStore.getAccountId()),
        Instant.parse(getRequiredField(root, OCCURRED_AT).asString()),
        eventStore.getVersion(),
        currency,
        Money.of(amount, currency));
  }
}
