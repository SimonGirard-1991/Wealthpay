package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.girardsimon.wealthpay.account.jooq.tables.Outbox.OUTBOX;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.girardsimon.wealthpay.account.application.AccountEventPublisher;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountEventSerializer;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository implements AccountEventPublisher {

  private final DSLContext dslContext;
  private final AccountEventSerializer accountEventSerializer;

  private final Clock clock;

  public OutboxRepository(
      DSLContext dslContext, AccountEventSerializer accountEventSerializer, Clock clock) {
    this.dslContext = dslContext;
    this.accountEventSerializer = accountEventSerializer;
    this.clock = clock;
  }

  @Override
  public void publish(List<AccountEvent> events) {
    if (events.isEmpty()) {
      return;
    }

    var insertStep =
        dslContext
            .insertInto(OUTBOX)
            .columns(
                OUTBOX.EVENT_ID,
                OUTBOX.AGGREGATE_TYPE,
                OUTBOX.AGGREGATE_ID,
                OUTBOX.AGGREGATE_VERSION,
                OUTBOX.EVENT_TYPE,
                OUTBOX.OCCURRED_AT,
                OUTBOX.PAYLOAD);

    for (AccountEvent event : events) {
      String eventType = event.getClass().getSimpleName();
      JSONB payload = accountEventSerializer.apply(event);

      insertStep.values(
          event.eventId().id(),
          "AccountEvent",
          event.accountId().id(),
          event.version(),
          eventType,
          OffsetDateTime.ofInstant(event.occurredAt(), clock.getZone()),
          payload);
    }

    insertStep.execute();
  }
}
