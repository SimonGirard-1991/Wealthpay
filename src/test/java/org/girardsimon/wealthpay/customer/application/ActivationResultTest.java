package org.girardsimon.wealthpay.customer.application;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class ActivationResultTest {

  @Test
  void rejects_a_missing_activation_instant() {
    // Act + Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new ActivationResult(null, false));
  }
}
