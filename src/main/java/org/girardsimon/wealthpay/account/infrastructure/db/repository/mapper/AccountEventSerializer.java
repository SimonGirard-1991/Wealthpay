package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCancelled;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.function.Function;

@Component
public class AccountEventSerializer implements Function<AccountEvent, JSONB> {

    private final ObjectMapper objectMapper;

    public AccountEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JSONB apply(AccountEvent accountEvent) {
        return switch (accountEvent) {
            case AccountClosed accountClosed -> null;
            case AccountOpened accountOpened -> mapAccountOpenedPayload(accountOpened);
            case FundsCredited fundsCredited -> null;
            case FundsDebited fundsDebited -> null;
            case FundsReserved fundsReserved -> null;
            case ReservationCancelled reservationCancelled -> null;
        };
    }

    private JSONB mapAccountOpenedPayload(AccountOpened accountOpened) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putPOJO("currency", accountOpened.currency().name());
        root.putPOJO("initialBalance", accountOpened.initialBalance().amount());
        root.putPOJO("occurredAt", accountOpened.occurredAt().toString());

        String jsonString = objectMapper.writeValueAsString(root);
        return JSONB.valueOf(jsonString);
    }
}
