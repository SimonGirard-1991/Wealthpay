package org.girardsimon.wealthpay.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdmissionDecisionIdTest {

  @Test
  void wraps_the_given_uuid() {
    // Arrange
    UUID uuid = UUID.randomUUID();

    // Act
    AdmissionDecisionId decisionId = new AdmissionDecisionId(uuid);

    // Assert
    assertThat(decisionId.id()).isEqualTo(uuid);
  }

  @Test
  void rejects_a_null_uuid() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new AdmissionDecisionId(null));
  }
}
