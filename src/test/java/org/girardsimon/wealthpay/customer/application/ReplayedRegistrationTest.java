package org.girardsimon.wealthpay.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.CustomerId;
import org.junit.jupiter.api.Test;

class ReplayedRegistrationTest {

  @Test
  void carries_the_customer_id_the_replay_re_reads() {
    // Arrange
    CustomerId customerId = CustomerId.of(UUID.randomUUID());

    // Act
    ReplayedRegistration replayed = new ReplayedRegistration(customerId);

    // Assert
    assertThat(replayed.customerId()).isEqualTo(customerId);
  }

  @Test
  void rejects_a_missing_customer_id() {
    // Act ... Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> new ReplayedRegistration(null));
  }
}
