package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import static org.girardsimon.wealthpay.account.utils.MoneyDeserializerUtils.extractMoney;
import static org.girardsimon.wealthpay.shared.utils.MapperUtils.getRequiredField;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
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
import org.girardsimon.wealthpay.account.utils.AccountEventType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventStoreEntryToAccountEventMapper implements Function<EventStore, AccountEvent> {

  private static final String OCCURRED_AT = "occurredAt";
  private static final String CURRENCY = "currency";
  private static final String RESERVATION_ID = "reservationId";
  private static final String INITIAL_BALANCE = "initialBalance";
  private static final String TRANSACTION_ID = "transactionId";

  private final ObjectMapper objectMapper;

  public EventStoreEntryToAccountEventMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  private static AccountEventMeta getAccountEventMeta(EventStore eventStore, JsonNode root) {
    return AccountEventMeta.of(
        EventId.of(eventStore.getEventId()),
        AccountId.of(eventStore.getAccountId()),
        Instant.parse(getRequiredField(root, OCCURRED_AT).asString()),
        eventStore.getVersion());
  }

  @Override
  public AccountEvent apply(EventStore eventStore) {
    AccountEventType accountEventType = AccountEventType.from(eventStore.getEventType());

    return switch (accountEventType) {
      case ACCOUNT_OPENED -> mapAccountOpened(eventStore);
      case ACCOUNT_CLOSED -> mapAccountClosed(eventStore);
      case RESERVATION_CAPTURED -> mapReservationCaptured(eventStore);
      case FUNDS_CREDITED -> mapFundsCredited(eventStore);
      case FUNDS_DEBITED -> mapFundsDebited(eventStore);
      case FUNDS_RESERVED -> mapFundsReserved(eventStore);
      case RESERVATION_CANCELED -> mapReservationCanceled(eventStore);
    };
  }

  private ReservationCanceled mapReservationCanceled(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    String reservationId = getRequiredField(root, RESERVATION_ID).asString();

    return new ReservationCanceled(
        getAccountEventMeta(eventStore, root),
        ReservationId.of(UUID.fromString(reservationId)),
        extractMoney(root));
  }

  private FundsReserved mapFundsReserved(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    String transactionId = getRequiredField(root, TRANSACTION_ID).asString();
    String reservationId = getRequiredField(root, RESERVATION_ID).asString();

    return new FundsReserved(
        getAccountEventMeta(eventStore, root),
        TransactionId.of(UUID.fromString(transactionId)),
        ReservationId.of(UUID.fromString(reservationId)),
        extractMoney(root));
  }

  private FundsDebited mapFundsDebited(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    String transactionId = getRequiredField(root, TRANSACTION_ID).asString();

    return new FundsDebited(
        getAccountEventMeta(eventStore, root),
        TransactionId.of(UUID.fromString(transactionId)),
        extractMoney(root));
  }

  private FundsCredited mapFundsCredited(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    String transactionId = getRequiredField(root, TRANSACTION_ID).asString();

    return new FundsCredited(
        getAccountEventMeta(eventStore, root),
        TransactionId.of(UUID.fromString(transactionId)),
        extractMoney(root));
  }

  private AccountClosed mapAccountClosed(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());
    return new AccountClosed(getAccountEventMeta(eventStore, root));
  }

  private ReservationCaptured mapReservationCaptured(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    String reservationId = getRequiredField(root, RESERVATION_ID).asString();

    return new ReservationCaptured(
        getAccountEventMeta(eventStore, root),
        ReservationId.of(UUID.fromString(reservationId)),
        extractMoney(root));
  }

  private AccountOpened mapAccountOpened(EventStore eventStore) {
    JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

    SupportedCurrency currency =
        SupportedCurrency.fromValue(getRequiredField(root, CURRENCY).asString());
    BigDecimal amount = getRequiredField(root, INITIAL_BALANCE).decimalValue();

    return new AccountOpened(
        getAccountEventMeta(eventStore, root), currency, Money.of(amount, currency));
  }
}
