package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import org.girardsimon.wealthpay.account.jooq.tables.pojos.EventStore;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Function;

@Component
public class EventStoreEntryToAccountEventMapper implements Function<EventStore, AccountEvent> {

    private final ObjectMapper objectMapper;

    public EventStoreEntryToAccountEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AccountEvent apply(EventStore eventStore) {
        String eventType = eventStore.getEventType();

        return switch (eventType) {
            case "AccountOpened" -> mapAccountOpened(eventStore);
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }

    private AccountOpened mapAccountOpened(EventStore eventStore) {
        JsonNode root = objectMapper.readTree(eventStore.getPayload().data());

        SupportedCurrency currency = SupportedCurrency.valueOf(root.get("currency").asString());
        BigDecimal amount = root.get("initialBalance").decimalValue();

        return new AccountOpened(
                AccountId.of(eventStore.getAccountId()),
                Instant.parse(root.get("occurredAt").asString()),
                eventStore.getVersion(),
                currency,
                Money.of(amount, currency)
        );

    }
}
