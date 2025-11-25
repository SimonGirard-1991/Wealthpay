package org.girardsimon.wealthpay.account.application.view;

import org.girardsimon.wealthpay.account.domain.model.Money;

import java.util.UUID;

public record AccountBalanceView(
        UUID accountId,
        Money balance,
        Money reservedFunds,
        String status,
        long version
) {
}
