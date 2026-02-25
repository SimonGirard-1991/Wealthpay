package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.girardsimon.wealthpay.account.jooq.tables.AccountSnapshot.ACCOUNT_SNAPSHOT;

import java.util.Optional;
import org.girardsimon.wealthpay.account.application.AccountSnapshotStore;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountSnapshot;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotDeserializer;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountSnapshotSerializer;
import org.girardsimon.wealthpay.account.jooq.tables.records.AccountSnapshotRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class AccountSnapshotRepository implements AccountSnapshotStore {

  private final DSLContext dslContext;

  private final AccountSnapshotDeserializer accountSnapshotDeserializer;
  private final AccountSnapshotSerializer accountSnapshotSerializer;

  public AccountSnapshotRepository(
      DSLContext dslContext,
      AccountSnapshotDeserializer accountSnapshotDeserializer,
      AccountSnapshotSerializer accountSnapshotSerializer) {
    this.dslContext = dslContext;
    this.accountSnapshotDeserializer = accountSnapshotDeserializer;
    this.accountSnapshotSerializer = accountSnapshotSerializer;
  }

  @Override
  public Optional<AccountSnapshot> load(AccountId accountId) {
    return dslContext
        .select(ACCOUNT_SNAPSHOT.ACCOUNT_ID, ACCOUNT_SNAPSHOT.STATE, ACCOUNT_SNAPSHOT.VERSION)
        .from(ACCOUNT_SNAPSHOT)
        .where(ACCOUNT_SNAPSHOT.ACCOUNT_ID.eq(accountId.id()))
        .fetchOptional()
        .flatMap(accountSnapshotDeserializer);
  }

  @Override
  public void saveSnapshot(AccountSnapshot accountSnapshot) {
    AccountSnapshotRecord row = accountSnapshotSerializer.apply(accountSnapshot);

    dslContext
        .insertInto(ACCOUNT_SNAPSHOT)
        .set(row)
        .onConflict(ACCOUNT_SNAPSHOT.ACCOUNT_ID)
        .doUpdate()
        .set(row)
        .where(ACCOUNT_SNAPSHOT.VERSION.lt(accountSnapshot.version()))
        .execute();
  }
}
