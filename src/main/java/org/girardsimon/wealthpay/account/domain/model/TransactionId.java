package org.girardsimon.wealthpay.account.domain.model;

import java.util.UUID;

public record TransactionId(UUID id) {

    public TransactionId {
        if (id == null) {
            throw new IllegalArgumentException("Id must not be null");
        }
    }

    public static TransactionId newId() {
        return new TransactionId(UUID.randomUUID());
    }

    public static TransactionId of(UUID id) {
        return new TransactionId(id);
    }
}
