package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.girardsimon.wealthpay.account.jooq.tables.ProcessedTransactions.PROCESSED_TRANSACTIONS;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.selectOne;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.girardsimon.wealthpay.account.application.ProcessedTransactionStore;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.domain.exception.TransactionIdConflictException;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.jooq.CommonTableExpression;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
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
      AccountId accountId, TransactionId transactionId, String fingerprint, Instant occurredAt) {
    OffsetDateTime timestamp = OffsetDateTime.ofInstant(occurredAt, clock.getZone());

    /*
     * Single round-trip idempotency check using a CTE:
     *  1. INSERT ... ON CONFLICT DO NOTHING RETURNING → returns the row only if inserted
     *  2. UNION ALL SELECT from the table WHERE NOT EXISTS(ins)  →  returns the existing row on conflict
     *
     * Avoids ON CONFLICT DO UPDATE (no-op writes generate WAL, dead tuples, and fire triggers).
     */
    String insertedColumnName = "inserted";
    String fingerprintColumnName = "fingerprint";
    CommonTableExpression<Record2<String, Boolean>> insertAttempt =
        name("insert_attempt")
            .fields(fingerprintColumnName, insertedColumnName)
            .as(
                dslContext
                    .insertInto(PROCESSED_TRANSACTIONS)
                    .set(PROCESSED_TRANSACTIONS.ACCOUNT_ID, accountId.id())
                    .set(PROCESSED_TRANSACTIONS.TRANSACTION_ID, transactionId.id())
                    .set(PROCESSED_TRANSACTIONS.FINGERPRINT, fingerprint)
                    .set(PROCESSED_TRANSACTIONS.OCCURRED_AT, timestamp)
                    .onConflict(
                        PROCESSED_TRANSACTIONS.ACCOUNT_ID, PROCESSED_TRANSACTIONS.TRANSACTION_ID)
                    .doNothing()
                    .returningResult(
                        PROCESSED_TRANSACTIONS.FINGERPRINT, inline(true).as(insertedColumnName)));

    Field<String> cteFingerprint = insertAttempt.field(fingerprintColumnName, String.class);
    Field<Boolean> cteInserted = insertAttempt.field(insertedColumnName, Boolean.class);

    Record2<String, Boolean> result =
        dslContext
            .with(insertAttempt)
            .select(cteFingerprint, cteInserted)
            .from(insertAttempt)
            .unionAll(
                dslContext
                    .select(
                        PROCESSED_TRANSACTIONS.FINGERPRINT, inline(false).as(insertedColumnName))
                    .from(PROCESSED_TRANSACTIONS)
                    .where(PROCESSED_TRANSACTIONS.ACCOUNT_ID.eq(accountId.id()))
                    .and(PROCESSED_TRANSACTIONS.TRANSACTION_ID.eq(transactionId.id()))
                    .andNotExists(selectOne().from(insertAttempt)))
            .fetchOne();

    if (result != null && result.value2()) {
      return TransactionStatus.COMMITTED;
    }

    String storedFingerprint =
        result != null ? result.value1() : fallbackFingerprint(accountId, transactionId);

    if (storedFingerprint != null && !storedFingerprint.equals(fingerprint)) {
      throw new TransactionIdConflictException(accountId, transactionId);
    }

    return TransactionStatus.NO_EFFECT;
  }

  /*
   * Fallback for the rare case where two concurrent transactions race on the same
   * transaction ID: the CTE returns nothing because the INSERT conflicts (DO NOTHING)
   * and the UNION ALL SELECT cannot see the other transaction's uncommitted row
   * (same snapshot). A separate SELECT gets a fresh snapshot and reads the now-committed row.
   */
  private String fallbackFingerprint(AccountId accountId, TransactionId transactionId) {
    return dslContext
        .select(PROCESSED_TRANSACTIONS.FINGERPRINT)
        .from(PROCESSED_TRANSACTIONS)
        .where(PROCESSED_TRANSACTIONS.ACCOUNT_ID.eq(accountId.id()))
        .and(PROCESSED_TRANSACTIONS.TRANSACTION_ID.eq(transactionId.id()))
        .fetchOne(PROCESSED_TRANSACTIONS.FINGERPRINT);
  }
}
