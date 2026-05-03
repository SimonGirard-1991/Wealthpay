package org.girardsimon.wealthpay.account.infrastructure.id;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RandomReservationIdGeneratorTest {

  @Test
  void newId_returns_a_uuidv7() {
    // Arrange
    RandomReservationIdGenerator generator = new RandomReservationIdGenerator();

    // Act
    var id = generator.newId();

    // Assert
    assertThat(id.id().version()).isEqualTo(7);
  }
}
