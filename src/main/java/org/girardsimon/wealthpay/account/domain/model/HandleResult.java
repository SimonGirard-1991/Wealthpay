package org.girardsimon.wealthpay.account.domain.model;

import java.util.List;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;

public sealed interface HandleResult permits SimpleCommandResult, ReservationOutcome {
  List<AccountEvent> events();

  default boolean hasEffect() {
    return !events().isEmpty();
  }
}
