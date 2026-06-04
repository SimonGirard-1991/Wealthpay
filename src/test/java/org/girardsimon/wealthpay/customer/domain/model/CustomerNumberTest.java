package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.girardsimon.wealthpay.customer.domain.exception.InvalidCustomerNumberException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerNumberTest {

  @Test
  void of_accepts_a_valid_ten_digit_luhn_number() {
    // Arrange
    String value = "1234567897";

    // Act
    CustomerNumber customerNumber = CustomerNumber.of(value);

    // Assert
    assertThat(customerNumber.value()).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1234567890", // valid format, wrong Luhn check digit
        "123456789", // fewer than ten digits
        "12345678901", // more than ten digits
        "1234X67897" // contains a non-digit
      })
  void rejects_a_value_that_is_not_a_valid_customer_number(String invalid) {
    // Act ... Assert
    assertThatExceptionOfType(InvalidCustomerNumberException.class)
        .isThrownBy(() -> CustomerNumber.of(invalid));
  }

  @Test
  void rejects_null() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> CustomerNumber.of(null));
  }
}
