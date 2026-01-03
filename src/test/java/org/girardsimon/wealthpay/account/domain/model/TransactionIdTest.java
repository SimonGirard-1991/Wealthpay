package org.girardsimon.wealthpay.account.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TransactionIdTest {

    @Test
    void check_transaction_id_consistency() {
        // Arrange ... Act ... Assert
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new TransactionId(null));
    }

}