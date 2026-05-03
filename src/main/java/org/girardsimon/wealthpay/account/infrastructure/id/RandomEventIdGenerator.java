package org.girardsimon.wealthpay.account.infrastructure.id;

import com.github.f4b6a3.uuid.UuidCreator;
import org.girardsimon.wealthpay.account.domain.model.EventId;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.springframework.stereotype.Component;

/**
 * Mints {@link EventId}s as UUIDv7 (RFC 9562) for B-tree insert locality on append-heavy indexes.
 */
@Component
public class RandomEventIdGenerator implements EventIdGenerator {
  @Override
  public EventId newId() {
    return EventId.of(UuidCreator.getTimeOrderedEpoch());
  }
}
