package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.girardsimon.wealthpay.account.jooq.tables.ProcessedTransactions.PROCESSED_TRANSACTIONS;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.girardsimon.wealthpay.account.application.ProcessedTransactionStore;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.jooq.tables.records.ProcessedTransactionsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedTransactionRepository implements ProcessedTransactionStore {

  private final DSLContext dslContext;
  private final Clock clock;

  public ProcessedTransactionRepository(DSLContext dslContext, Clock clock) {
    this.dslContext = dslContext;
    this.clock = clock;
  }

  @Override
  public TransactionStatus register(
      AccountId accountId, TransactionId transactionId, Instant occurredAt) {

    ProcessedTransactionsRecord row = dslContext.newRecord(PROCESSED_TRANSACTIONS);
    row.setAccountId(accountId.id());
    row.setTransactionId(transactionId.id());
    row.setOccurredAt(OffsetDateTime.ofInstant(occurredAt, clock.getZone()));
    int insertedRowCount =
        dslContext.insertInto(PROCESSED_TRANSACTIONS).set(row).onConflictDoNothing().execute();
    return insertedRowCount > 0 ? TransactionStatus.COMMITTED : TransactionStatus.NO_EFFECT;
  }
}
