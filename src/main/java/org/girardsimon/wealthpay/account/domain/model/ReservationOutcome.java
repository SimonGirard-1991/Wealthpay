package org.girardsimon.wealthpay.account.domain.model;

import java.util.List;
import java.util.Optional;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;

public record ReservationOutcome(List<AccountEvent> events, Optional<Money> capturedMoney)
    implements HandleResult {}
