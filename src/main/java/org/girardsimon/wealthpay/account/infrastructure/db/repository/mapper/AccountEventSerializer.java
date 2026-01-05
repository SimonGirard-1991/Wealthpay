package org.girardsimon.wealthpay.account.infrastructure.db.repository.mapper;

import org.girardsimon.wealthpay.account.domain.event.AccountClosed;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.event.AccountOpened;
import org.girardsimon.wealthpay.account.domain.event.FundsCredited;
import org.girardsimon.wealthpay.account.domain.event.FundsDebited;
import org.girardsimon.wealthpay.account.domain.event.FundsReserved;
import org.girardsimon.wealthpay.account.domain.event.ReservationCancelled;
import org.girardsimon.wealthpay.account.domain.event.ReservationCaptured;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.function.Function;

@Component
public class AccountEventSerializer implements Function<AccountEvent, JSONB> {

    public static final String OCCURRED_AT = "occurredAt";
    public static final String CURRENCY = "currency";
    public static final String AMOUNT = "amount";
    public static final String TRANSACTION_ID = "transactionId";
    public static final String RESERVATION_ID = "reservationId";
    public static final String INITIAL_BALANCE = "initialBalance";

    private final ObjectMapper objectMapper;

    public AccountEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JSONB apply(AccountEvent accountEvent) {
        ObjectNode root = objectMapper.createObjectNode();
        switch (accountEvent) {
            case AccountClosed accountClosed -> mapAccountClosedPayload(root, accountClosed);
            case AccountOpened accountOpened -> mapAccountOpenedPayload(root, accountOpened);
            case FundsCredited fundsCredited -> mapFundsCreditedPayload(root, fundsCredited);
            case FundsDebited fundsDebited -> mapFundsDebitedPayload(root, fundsDebited);
            case FundsReserved fundsReserved -> mapFundsReservedPayload(root, fundsReserved);
            case ReservationCancelled reservationCancelled -> mapReservationCancelledPayload(root, reservationCancelled);
            case ReservationCaptured reservationCaptured -> mapReservationCapturedPayload(root, reservationCaptured);
        }

        String jsonString = objectMapper.writeValueAsString(root);
        return JSONB.valueOf(jsonString);
    }

    private static void mapReservationCancelledPayload(ObjectNode root, ReservationCancelled reservationCancelled) {
        root.putPOJO(RESERVATION_ID, reservationCancelled.reservationId().id().toString());
        root.putPOJO(CURRENCY, reservationCancelled.money().currency().name());
        root.putPOJO(AMOUNT, reservationCancelled.money().amount());
        root.putPOJO(OCCURRED_AT, reservationCancelled.occurredAt().toString());
    }

    private static void mapFundsReservedPayload(ObjectNode root, FundsReserved fundsReserved) {
        root.putPOJO(RESERVATION_ID, fundsReserved.reservationId().id().toString());
        root.putPOJO(CURRENCY, fundsReserved.money().currency().name());
        root.putPOJO(AMOUNT, fundsReserved.money().amount());
        root.putPOJO(OCCURRED_AT, fundsReserved.occurredAt().toString());
    }

    private static void mapFundsCreditedPayload(ObjectNode root, FundsCredited fundsCredited) {
        root.putPOJO(TRANSACTION_ID, fundsCredited.transactionId().id().toString());
        root.putPOJO(CURRENCY, fundsCredited.money().currency().name());
        root.putPOJO(AMOUNT, fundsCredited.money().amount());
        root.putPOJO(OCCURRED_AT, fundsCredited.occurredAt().toString());
    }

    private static void mapFundsDebitedPayload(ObjectNode root, FundsDebited fundsDebited) {
        root.putPOJO(TRANSACTION_ID, fundsDebited.transactionId().id().toString());
        root.putPOJO(CURRENCY, fundsDebited.money().currency().name());
        root.putPOJO(AMOUNT, fundsDebited.money().amount());
        root.putPOJO(OCCURRED_AT, fundsDebited.occurredAt().toString());
    }

    private static void mapAccountClosedPayload(ObjectNode root, AccountClosed accountClosed) {
        root.putPOJO(OCCURRED_AT, accountClosed.occurredAt().toString());
    }

    private static void mapReservationCapturedPayload(ObjectNode root, ReservationCaptured reservationCaptured) {
        root.putPOJO(RESERVATION_ID, reservationCaptured.reservationId().id().toString());
        root.putPOJO(CURRENCY, reservationCaptured.money().currency().name());
        root.putPOJO(AMOUNT, reservationCaptured.money().amount());
        root.putPOJO(OCCURRED_AT, reservationCaptured.occurredAt().toString());
    }

    private static void mapAccountOpenedPayload(ObjectNode root, AccountOpened accountOpened) {
        root.putPOJO(CURRENCY, accountOpened.currency().name());
        root.putPOJO(INITIAL_BALANCE, accountOpened.initialBalance().amount());
        root.putPOJO(OCCURRED_AT, accountOpened.occurredAt().toString());
    }
}
