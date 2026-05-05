package org.girardsimon.wealthpay.account.infrastructure.id;

import java.util.UUID;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.springframework.stereotype.Component;

/**
 * Mints {@link AccountId}s as UUIDv4. AccountId is externally exposed via the REST API; UUIDv7's
 * embedded millisecond timestamp would leak account-creation time to clients, so v4 is the
 * deliberate choice here. The internal {@code EventId} and {@code ReservationId} use UUIDv7 for
 * B-tree insert locality — they are never exposed externally.
 */
@Component
public class RandomAccountIdGenerator implements AccountIdGenerator {

  @Override
  public AccountId newId() {
    return AccountId.of(UUID.randomUUID());
  }
}
