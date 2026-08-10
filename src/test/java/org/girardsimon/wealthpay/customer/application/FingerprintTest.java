package org.girardsimon.wealthpay.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class FingerprintTest {

  private static final String VALID = "a".repeat(64);

  @Test
  void wraps_a_lowercase_sha256_hex_digest() {
    // Act
    Fingerprint fingerprint = new Fingerprint(VALID);

    // Assert
    assertThat(fingerprint.value()).isEqualTo(VALID);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "abc",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", // uppercase
        "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", // not hex
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 63 chars
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" // 65 chars
      })
  void rejects_anything_that_is_not_a_lowercase_sha256_hex_digest(String candidate) {
    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new Fingerprint(candidate));
  }
}
