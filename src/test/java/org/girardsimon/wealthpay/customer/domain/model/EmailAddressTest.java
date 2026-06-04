package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.girardsimon.wealthpay.customer.domain.exception.InvalidEmailAddressException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

  @Test
  void of_accepts_a_valid_email_address() {
    // Arrange
    String value = "john.doe@example.com";

    // Act
    EmailAddress email = EmailAddress.of(value);

    // Assert
    assertThat(email.value()).isEqualTo("john.doe@example.com");
  }

  @Test
  void normalizes_to_lowercase_canonical_form() {
    // Arrange
    String mixedCase = "John.Doe@Example.COM";

    // Act
    EmailAddress email = EmailAddress.of(mixedCase);

    // Assert
    assertThat(email.value()).isEqualTo("john.doe@example.com");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "john.doe.example.com", // no @
        "john@doe@example.com", // multiple @
        "john@examplecom", // domain without a dot
        "john doe@example.com", // contains whitespace
        "" // empty
      })
  void rejects_a_malformed_address(String malformed) {
    // Act ... Assert
    assertThatExceptionOfType(InvalidEmailAddressException.class)
        .isThrownBy(() -> EmailAddress.of(malformed));
  }

  @Test
  void accepts_an_address_at_the_maximum_length() {
    // Arrange
    String maxLength = "a".repeat(242) + "@example.com"; // exactly 254 chars

    // Act
    EmailAddress email = EmailAddress.of(maxLength);

    // Assert
    assertThat(email.value()).hasSize(254);
  }

  @Test
  void rejects_an_address_exceeding_max_length() {
    // Arrange
    String tooLong = "a".repeat(243) + "@example.com"; // 255 chars, one over the limit

    // Act ... Assert
    assertThatExceptionOfType(InvalidEmailAddressException.class)
        .isThrownBy(() -> EmailAddress.of(tooLong));
  }

  @Test
  void rejects_null() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> EmailAddress.of(null));
  }
}
