package org.girardsimon.wealthpay.account.domain.event;

import java.time.Instant;
import org.girardsimon.wealthpay.account.domain.model.AccountId;

public record AccountClosed(AccountId accountId, Instant occurredAt, long version)
    implements AccountEvent {}
