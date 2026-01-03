package org.girardsimon.wealthpay.account.domain.event;

import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;

import java.time.Instant;

public record FundsDebited(
        TransactionId transactionId,
        AccountId accountId,
        Instant occurredAt,
        long version,
        Money amount
) implements AccountEvent {
}
