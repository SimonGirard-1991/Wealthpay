package org.girardsimon.wealthpay.account.domain.event;

import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;

import java.time.Instant;

public record ReservationCancelled (
        AccountId accountId,
        Instant occurredAt,
        long version,
        ReservationId reservationId,
        Money money
) implements AccountEvent {
}
