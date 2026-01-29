package org.girardsimon.wealthpay.account.domain.event;

import java.time.Instant;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;

public record AccountOpened(
    AccountId accountId,
    Instant occurredAt,
    long version,
    SupportedCurrency currency,
    Money initialBalance)
    implements AccountEvent {}
