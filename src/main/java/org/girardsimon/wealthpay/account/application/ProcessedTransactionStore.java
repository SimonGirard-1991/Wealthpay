package org.girardsimon.wealthpay.account.application;

import java.time.Instant;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;

public interface ProcessedTransactionStore {
  TransactionStatus register(
      AccountId accountId, TransactionId transactionId, String fingerprint, Instant occurredAt);
}
