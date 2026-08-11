package org.girardsimon.wealthpay.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.junit.jupiter.api.Test;

class TransitionOutcomeTest {

  @Test
  void one_updated_row_means_this_caller_applied_the_transition() {
    // Act
    TransitionOutcome outcome = TransitionOutcome.ofUpdatedRows(1);

    // Assert
    assertThat(outcome).isEqualTo(TransitionOutcome.APPLIED);
  }

  @Test
  void no_updated_row_means_another_caller_got_there_first() {
    // Act
    TransitionOutcome outcome = TransitionOutcome.ofUpdatedRows(0);

    // Assert
    assertThat(outcome).isEqualTo(TransitionOutcome.SUPERSEDED);
  }

  @Test
  void more_than_one_updated_row_is_corruption_rather_than_a_stronger_success() {
    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> TransitionOutcome.ofUpdatedRows(2));
  }

  @Test
  void a_negative_rowcount_is_corruption() {
    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> TransitionOutcome.ofUpdatedRows(-1));
  }
}
