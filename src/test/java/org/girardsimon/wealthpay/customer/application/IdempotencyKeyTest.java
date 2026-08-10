package org.girardsimon.wealthpay.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyKeyTest {

  private static final int MAX_LENGTH = 255;

  @Test
  void wraps_the_given_key() {
    // Act
    IdempotencyKey key = new IdempotencyKey("a3f1-registration-001");

    // Assert
    assertThat(key.value()).isEqualTo("a3f1-registration-001");
  }

  @Test
  void accepts_a_key_at_the_maximum_length() {
    // Arrange
    String longestAllowed = "k".repeat(MAX_LENGTH);

    // Act
    IdempotencyKey key = new IdempotencyKey(longestAllowed);

    // Assert
    assertThat(key.value()).hasSize(MAX_LENGTH);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t"})
  void rejects_a_blank_key(String candidate) {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new IdempotencyKey(candidate));
  }

  @Test
  void rejects_a_key_past_the_maximum_length() {
    // Arrange
    String tooLong = "k".repeat(MAX_LENGTH + 1);

    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new IdempotencyKey(tooLong));
  }

  @ParameterizedTest
  @ValueSource(strings = {" a3f1", "a3f1 ", "\ta3f1"})
  void rejects_surrounding_whitespace_rather_than_trimming_it(String candidate) {
    // Act ... Assert - trimming would silently merge two keys the client meant to keep apart
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new IdempotencyKey(candidate));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a3f1\nWARN forged audit line", "a3f1\rx", "a3f1\0x"})
  void rejects_control_characters_that_could_forge_a_log_line(String candidate) {
    // Act ... Assert - this value is a correlation handle and reaches plain-text logs
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new IdempotencyKey(candidate));
  }
}
