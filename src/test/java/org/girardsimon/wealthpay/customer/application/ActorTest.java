package org.girardsimon.wealthpay.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ActorTest {

  private static final int MAX_LENGTH = 255;

  @Test
  void wraps_a_subject_identifier() {
    // Act
    Actor actor = new Actor("officer-42");

    // Assert
    assertThat(actor.value()).isEqualTo("officer-42");
  }

  @Test
  void names_the_system_actor() {
    // Assert
    assertThat(Actor.SYSTEM.value()).isEqualTo("SYSTEM");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t"})
  void rejects_a_blank_actor(String candidate) {
    // Act ... Assert - the column's CHECK only rejects the empty string
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> new Actor(candidate));
  }

  @ParameterizedTest
  @ValueSource(strings = {" officer-42", "officer-42 ", "officer\n42"})
  void rejects_a_padded_or_control_bearing_actor(String candidate) {
    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> new Actor(candidate));
  }

  @Test
  void accepts_an_actor_at_the_maximum_length() {
    // Arrange
    String longestAllowed = "a".repeat(MAX_LENGTH);

    // Act
    Actor actor = new Actor(longestAllowed);

    // Assert
    assertThat(actor.value()).hasSize(MAX_LENGTH);
  }

  @Test
  void rejects_an_actor_past_the_maximum_length() {
    // Arrange
    String tooLong = "a".repeat(MAX_LENGTH + 1);

    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> new Actor(tooLong));
  }
}
