package org.girardsimon.wealthpay.account.infrastructure.id;

import org.girardsimon.wealthpay.account.domain.model.EventId;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.springframework.stereotype.Component;

@Component
public class RandomEventIdGenerator implements EventIdGenerator {
  @Override
  public EventId newId() {
    return EventId.newId();
  }
}
