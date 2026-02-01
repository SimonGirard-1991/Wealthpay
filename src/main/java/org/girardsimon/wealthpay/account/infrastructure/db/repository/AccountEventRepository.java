package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.girardsimon.wealthpay.account.jooq.tables.EventStore.EVENT_STORE;
import static org.jooq.impl.DSL.max;

import java.util.List;
import java.util.UUID;
import org.girardsimon.wealthpay.account.application.AccountEventStore;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountEventSerializer;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.EventStoreEntryToAccountEventMapper;
import org.girardsimon.wealthpay.account.jooq.tables.pojos.EventStore;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
public class AccountEventRepository implements AccountEventStore {

  private final DSLContext dslContext;
  private final EventStoreEntryToAccountEventMapper eventStoreEntryToAccountEventMapper;
  private final AccountEventSerializer accountEventSerializer;

  public AccountEventRepository(
      DSLContext dslContext,
      EventStoreEntryToAccountEventMapper eventStoreEntryToAccountEventMapper,
      AccountEventSerializer accountEventSerializer) {
    this.dslContext = dslContext;
    this.eventStoreEntryToAccountEventMapper = eventStoreEntryToAccountEventMapper;
    this.accountEventSerializer = accountEventSerializer;
  }

  @Override
  public List<AccountEvent> loadEvents(AccountId accountId) {
    UUID accountUuid = accountId.id();

    List<EventStore> rows =
        dslContext
            .select(
                EVENT_STORE.ID,
                EVENT_STORE.ACCOUNT_ID,
                EVENT_STORE.VERSION,
                EVENT_STORE.EVENT_TYPE,
                EVENT_STORE.PAYLOAD,
                EVENT_STORE.CREATED_AT)
            .from(EVENT_STORE)
            .where(EVENT_STORE.ACCOUNT_ID.eq(accountUuid))
            .orderBy(EVENT_STORE.VERSION.asc())
            .fetchInto(EventStore.class);

    return rows.stream().map(eventStoreEntryToAccountEventMapper).toList();
  }

  @Override
  public void appendEvents(AccountId accountId, long expectedVersion, List<AccountEvent> events) {
    if (events.isEmpty()) {
      return;
    }

    UUID accountUuid = accountId.id();

    Long currentVersion =
        dslContext
            .select(max(EVENT_STORE.VERSION))
            .from(EVENT_STORE)
            .where(EVENT_STORE.ACCOUNT_ID.eq(accountUuid))
            .fetchOneInto(Long.class);

    long actualVersion = currentVersion != null ? currentVersion : 0L;

    if (actualVersion != expectedVersion) {
      throw new OptimisticLockingFailureException(
          "Version mismatch for account %s: expected %d but found %d"
              .formatted(accountUuid, expectedVersion, actualVersion));
    }

    long nextExpectedVersion = actualVersion;

    try {
      for (AccountEvent event : events) {
        nextExpectedVersion++;
        if (event.version() != nextExpectedVersion) {
          throw new IllegalStateException( // This indicates a bug in the calling code, not a
              // concurrency issue.
              "Event version gap: expected %d but got %d for account %s"
                  .formatted(nextExpectedVersion, event.version(), accountUuid));
        }
        String eventType = event.getClass().getSimpleName();
        JSONB payload = accountEventSerializer.apply(event);

        dslContext
            .insertInto(EVENT_STORE)
            .columns(
                EVENT_STORE.ACCOUNT_ID,
                EVENT_STORE.VERSION,
                EVENT_STORE.EVENT_TYPE,
                EVENT_STORE.PAYLOAD)
            .values(accountUuid, event.version(), eventType, payload)
            .execute();
      }
    } catch (DataIntegrityViolationException e) {
      throw new OptimisticLockingFailureException(
          "Concurrent modification detected for account %s".formatted(accountUuid), e);
    }
  }
}
