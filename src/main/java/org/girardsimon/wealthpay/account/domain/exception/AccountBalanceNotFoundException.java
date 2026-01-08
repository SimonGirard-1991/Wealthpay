package org.girardsimon.wealthpay.account.domain.exception;

import org.girardsimon.wealthpay.account.domain.model.AccountId;

public class AccountBalanceNotFoundException extends RuntimeException {
    public AccountBalanceNotFoundException(AccountId accountId) {
        super("No balance found for account %s".formatted(accountId));
    }
}
