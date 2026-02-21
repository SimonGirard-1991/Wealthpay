package org.girardsimon.wealthpay.account.domain.model;

import java.util.List;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;

public record ReservationOutcome(List<AccountEvent> events, Money capturedMoney)
    implements HandleResult {}
