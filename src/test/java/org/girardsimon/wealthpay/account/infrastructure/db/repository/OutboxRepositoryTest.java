package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.girardsimon.wealthpay.account.jooq.tables.Outbox.OUTBOX;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.girardsimon.wealthpay.account.domain.event.AccountEventMeta;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.EventId;
import org.girardsimon.wealthpay.account.domain.model.EventIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountEventSerializer;
import org.girardsimon.wealthpay.account.jooq.tables.records.OutboxRecord;
import org.girardsimon.wealthpay.account.testsupport.TestAccountIdGenerator;
import org.girardsimon.wealthpay.account.testsupport.TestEventIdGenerator;
import org.girardsimon.wealthpay.shared.config.TimeConfig;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

@JooqTest
@Import({
  OutboxRepository.class,
  AccountEventSerializer.class,
  TimeConfig.class,
  ObjectMapper.class
})
class OutboxRepositoryTest extends AbstractContainerTest {

  private final AccountIdGenerator accountIdGenerator = new TestAccountIdGenerator();
  private final EventIdGenerator eventIdGenerator = new TestEventIdGenerator();

  @Autowired private DSLContext dsl;
  @Autowired private Clock clock;
  @Autowired private OutboxRepository outboxRepository;

  @Test
  void publish_should_persist_events_in_outbox() {
    // Arrange
    EventId eventId = eventIdGenerator.newId();
    AccountId accountId = accountIdGenerator.newId();
    SupportedCurrency usd = SupportedCurrency.USD;
    Money initialBalance = Money.of(BigDecimal.TEN, usd);
    Instant occurredAt = Instant.now(clock);
    AccountEventMeta metaOpened = AccountEventMeta.of(eventId, accountId, occurredAt, 1L);
    AccountOpened opened = new AccountOpened(metaOpened, usd, initialBalance);

    // Act
    outboxRepository.publish(List.of(opened));

    // Assert
    OutboxRecord outboxRecord =
        dsl.selectFrom(OUTBOX).where(OUTBOX.EVENT_ID.eq(eventId.id())).fetchOne();
    assertAll(
        () -> assertThat(outboxRecord).isNotNull(),
        () -> assertThat(outboxRecord.getAggregateId()).isEqualTo(accountId.id()),
        () -> assertThat(outboxRecord.getAggregateType()).isEqualTo("AccountEvent"),
        () -> assertThat(outboxRecord.getAggregateVersion()).isEqualTo(1L),
        () -> assertThat(outboxRecord.getEventType()).isEqualTo("AccountOpened"));
  }
}
