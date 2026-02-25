package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import static org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotFields.AMOUNT;
import static org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotFields.BALANCE;
import static org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotFields.CURRENCY;
import static org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotFields.RESERVATIONS;
import static org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotFields.STATUS;

import java.util.function.Function;
import org.girardsimon.wealthpay.account.domain.model.AccountSnapshot;
import org.girardsimon.wealthpay.account.jooq.tables.records.AccountSnapshotRecord;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class AccountSnapshotSerializer implements Function<AccountSnapshot, AccountSnapshotRecord> {

  public static final long SCHEMA_VERSION = 1L;

  private final ObjectMapper objectMapper;

  public AccountSnapshotSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public AccountSnapshotRecord apply(AccountSnapshot accountSnapshot) {
    AccountSnapshotRecord accountSnapshotRecord = new AccountSnapshotRecord();
    accountSnapshotRecord.setAccountId(accountSnapshot.accountId().id());
    accountSnapshotRecord.setVersion(accountSnapshot.version());

    ObjectNode root = objectMapper.createObjectNode();
    root.putPOJO(CURRENCY, accountSnapshot.currency().name());
    root.putPOJO(BALANCE, accountSnapshot.balance().amount());
    root.putPOJO(STATUS, accountSnapshot.status().name());

    ObjectNode reservationsNode = objectMapper.createObjectNode();
    accountSnapshot
        .reservations()
        .forEach(
            (reservationId, money) -> {
              ObjectNode moneyNode = objectMapper.createObjectNode();
              moneyNode.putPOJO(AMOUNT, money.amount());
              moneyNode.putPOJO(CURRENCY, money.currency().name());
              reservationsNode.set(reservationId.id().toString(), moneyNode);
            });
    root.set(RESERVATIONS, reservationsNode);

    accountSnapshotRecord.setState(JSONB.valueOf(objectMapper.writeValueAsString(root)));
    accountSnapshotRecord.setSchemaVersion(SCHEMA_VERSION);

    return accountSnapshotRecord;
  }
}
