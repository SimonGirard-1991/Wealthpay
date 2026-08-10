package org.girardsimon.wealthpay.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.girardsimon.wealthpay.customer.domain.model.CustomerStatus;
import org.junit.jupiter.api.Test;

class StatusTransitionTest {

  @Test
  void pairs_the_two_ends_of_a_status_change() {
    // Act
    StatusTransition transition =
        new StatusTransition(CustomerStatus.ONBOARDING, CustomerStatus.ACTIVE);

    // Assert
    assertThat(transition.from()).isEqualTo(CustomerStatus.ONBOARDING);
    assertThat(transition.to()).isEqualTo(CustomerStatus.ACTIVE);
  }

  @Test
  void rejects_a_missing_source() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new StatusTransition(null, CustomerStatus.ACTIVE));
  }

  @Test
  void rejects_a_missing_target() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new StatusTransition(CustomerStatus.ONBOARDING, null));
  }

  @Test
  void rejects_a_transition_that_changes_nothing() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new StatusTransition(CustomerStatus.ACTIVE, CustomerStatus.ACTIVE));
  }
}
