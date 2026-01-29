package org.girardsimon.wealthpay.account.domain.model;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class TransactionIdTest {

  @Test
  void check_transaction_id_consistency() {
    // Arrange ... Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new TransactionId(null));
  }
}
