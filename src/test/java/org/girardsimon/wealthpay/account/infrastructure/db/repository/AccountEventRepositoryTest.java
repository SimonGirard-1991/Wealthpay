package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import org.girardsimon.wealthpay.account.application.AccountEventStore;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.AccountEventSerializer;
import org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper.EventStoreEntryToAccountEventMapper;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertAll;

@JooqTest
@Import({
        AccountEventRepository.class,
        EventStoreEntryToAccountEventMapper.class,
        AccountEventSerializer.class,
        ObjectMapper.class
})
class AccountEventRepositoryTest extends AbstractContainerTest {

    @Autowired
    private DSLContext dsl;
    @Autowired
    private AccountEventStore accountEventStore;

    @Test
    void loadEvents_should_return_deserialized_AccountOpened_event() {
        // Arrange
        AccountId accountId = AccountId.newId();
        String payloadJson = """
                {
                    "currency": "USD",
                    "initialBalance": 10,
                    "occurredAt": "2025-11-16T15:00:00Z"
                }
                """;
        dsl.insertInto(table(name("account", "event_store")))
                .columns(
                        field("account_id"),
                        field("version"),
                        field("event_type"),
                        field("payload")
                )
                .values(
                        accountId.id(),
                        1L,
                        "AccountOpened",
                        JSONB.valueOf(payloadJson)
                )
                .execute();

        // Act
        List<AccountEvent> events = accountEventStore.loadEvents(accountId);

        // Assert
        assertThat(events).hasSize(1);
        AccountEvent first = events.getFirst();
        assertThat(first).isInstanceOf(AccountOpened.class);
        AccountOpened accountOpened = (AccountOpened) first;
        assertAll(
                () -> assertThat(accountOpened.accountId()).isEqualTo(accountId),
                () -> assertThat(accountOpened.occurredAt()).isEqualTo(Instant.parse("2025-11-16T15:00:00Z")),
                () -> assertThat(accountOpened.version()).isEqualTo(1L),
                () -> assertThat(accountOpened.currency()).isEqualTo(SupportedCurrency.USD),
                () -> assertThat(accountOpened.initialBalance().amount()).isEqualTo(BigDecimal.valueOf(10L).setScale(2, RoundingMode.HALF_EVEN)),
                () -> assertThat(accountOpened.initialBalance().currency()).isEqualTo(SupportedCurrency.USD)
        );
    }

    @Test
    void appendEvents_persists_events_nominally() {
        // Arrange
        AccountId accountId = AccountId.newId();
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.TEN, usd);
        Instant occurredAt = Instant.parse("2025-11-16T15:00:00Z");

        AccountOpened opened = new AccountOpened(
                accountId,
                occurredAt,
                1L,
                usd,
                initialBalance
        );

        // Act
        accountEventStore.appendEvents(accountId, 0L, List.of(opened));

        // Assert
        List<AccountEvent> events = accountEventStore.loadEvents(accountId);
        assertThat(events).hasSize(1);
        AccountEvent first = events.getFirst();
        assertThat(first).isInstanceOf(AccountOpened.class);
        AccountOpened accountOpened = (AccountOpened) first;
        assertAll(
                () -> assertThat(accountOpened.accountId()).isEqualTo(accountId),
                () -> assertThat(accountOpened.occurredAt()).isEqualTo(Instant.parse("2025-11-16T15:00:00Z")),
                () -> assertThat(accountOpened.version()).isEqualTo(1L),
                () -> assertThat(accountOpened.currency()).isEqualTo(SupportedCurrency.USD),
                () -> assertThat(accountOpened.initialBalance().amount()).isEqualTo(BigDecimal.valueOf(10L).setScale(2, RoundingMode.HALF_EVEN)),
                () -> assertThat(accountOpened.initialBalance().currency()).isEqualTo(SupportedCurrency.USD)
        );
    }

    @Test
    void appendEvents_throws_OptimisticLock_when_expectedVersion_is_outdated() {
        // Arrange
        AccountId accountId = AccountId.newId();
        UUID accountUuid = accountId.id();
        dsl.insertInto(table(name("account", "event_store")))
                .columns(
                        field("account_id"),
                        field("version"),
                        field("event_type"),
                        field("payload")
                )
                .values(
                        accountUuid,
                        1L,
                        "AccountOpened",
                        JSONB.valueOf("{}")
                )
                .execute();
        SupportedCurrency usd = SupportedCurrency.USD;
        Money initialBalance = Money.of(BigDecimal.TEN, usd);
        AccountOpened opened = new AccountOpened(
                accountId,
                Instant.parse("2025-11-16T15:00:00Z"),
                1L,
                usd,
                initialBalance
        );

        // Act ... Assert
        List<AccountEvent> openedEvents = List.of(opened);
        assertThatExceptionOfType(OptimisticLockingFailureException.class)
                .isThrownBy(() -> accountEventStore.appendEvents(accountId, 0L, openedEvents));
    }
}