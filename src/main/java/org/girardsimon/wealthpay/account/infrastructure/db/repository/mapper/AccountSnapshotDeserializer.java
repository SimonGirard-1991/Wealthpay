package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import static org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotFields.BALANCE;
import static org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotFields.CURRENCY;
import static org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotFields.RESERVATIONS;
import static org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotFields.STATUS;
import static org.girardsimon.wealthpay.account.jooq.tables.AccountSnapshot.ACCOUNT_SNAPSHOT;
import static org.girardsimon.wealthpay.account.utils.MoneyDeserializerUtils.extractMoney;
import static org.girardsimon.wealthpay.shared.utils.MapperUtils.getRequiredField;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountSnapshot;
import org.girardsimon.wealthpay.account.domain.model.AccountStatus;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.jooq.JSONB;
import org.jooq.Record3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AccountSnapshotDeserializer
    implements Function<Record3<UUID, JSONB, Long>, Optional<AccountSnapshot>> {

  private static final Logger log = LoggerFactory.getLogger(AccountSnapshotDeserializer.class);

  private final ObjectMapper objectMapper;

  public AccountSnapshotDeserializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  private static Map<ReservationId, Money> extractReservations(JsonNode root) {
    JsonNode reservationsNode = getRequiredField(root, RESERVATIONS);

    Map<ReservationId, Money> reservations = new HashMap<>();
    for (Map.Entry<String, JsonNode> field : reservationsNode.properties()) {
      ReservationId reservationId = ReservationId.of(UUID.fromString(field.getKey()));
      reservations.put(reservationId, extractMoney(field.getValue()));
    }

    return reservations;
  }

  @Override
  public Optional<AccountSnapshot> apply(Record3<UUID, JSONB, Long> entry) {
    try {
      AccountId accountId = AccountId.of(entry.get(ACCOUNT_SNAPSHOT.ACCOUNT_ID));
      long version = entry.get(ACCOUNT_SNAPSHOT.VERSION);

      JSONB state = entry.get(ACCOUNT_SNAPSHOT.STATE);
      JsonNode root = objectMapper.readTree(state.data());

      SupportedCurrency currency =
          SupportedCurrency.fromValue(getRequiredField(root, CURRENCY).asString());
      Money balance = Money.of(getRequiredField(root, BALANCE).decimalValue(), currency);
      AccountStatus status = AccountStatus.valueOf(getRequiredField(root, STATUS).asString());
      Map<ReservationId, Money> reservations = extractReservations(root);

      return Optional.of(
          new AccountSnapshot(accountId, currency, balance, status, reservations, version));
    } catch (JacksonException | IllegalArgumentException | IllegalStateException e) {
      // Snapshot is performance optimization and should not block a critical path
      log.warn("Unable to deserialize account snapshot", e);
      return Optional.empty();
    }
  }
}
