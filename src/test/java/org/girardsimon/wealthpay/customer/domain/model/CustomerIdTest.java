package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@code CustomerId.of} must be called <strong>inside a method body</strong> here, not from a
 * {@code static final} field initialiser. Class initializers run once, before mutation, so a
 * factory exercised only from them has its null-return mutant survive unobserved - which is exactly
 * why this class exists while the other id types were already covered.
 */
class CustomerIdTest {

  @Test
  void wraps_the_given_uuid() {
    // Arrange
    UUID uuid = UUID.randomUUID();

    // Act
    CustomerId id = CustomerId.of(uuid);

    // Assert
    assertThat(id).isNotNull();
    assertThat(id.id()).isEqualTo(uuid);
  }

  @Test
  void rejects_a_null_uuid() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> CustomerId.of(null));
  }
}
