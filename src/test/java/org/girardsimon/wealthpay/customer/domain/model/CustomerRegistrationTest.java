package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CustomerRegistrationTest {

  private static final CustomerId ID = CustomerId.of(UUID.randomUUID());
  private static final CustomerNumber NUMBER = CustomerNumber.of("1234567897");
  private static final EmailAddress EMAIL = EmailAddress.of("john.doe@example.com");
  private static final Instant REGISTERED_AT = Instant.parse("2026-06-04T10:15:30Z");
  private static final CustomerDetails INDIVIDUAL =
      new IndividualDetails(
          PersonalName.of("John", "Doe"),
          LocalDate.of(1990, 1, 1),
          Gender.MALE,
          Nationalities.of(CountryCode.of("FR")),
          CountryCode.of("FR"));
  private static final CustomerDetails CORPORATE =
      new CorporateDetails("Acme S.A.", "RCS123456", CountryCode.of("FR"));

  @Test
  void register_starts_a_customer_in_onboarding_status() {
    // Act
    Customer customer = Customer.register(ID, NUMBER, EMAIL, INDIVIDUAL, REGISTERED_AT);

    // Assert
    assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ONBOARDING);
    assertThat(customer.getId()).isEqualTo(ID);
    assertThat(customer.getNumber()).isEqualTo(NUMBER);
    assertThat(customer.getEmail()).isEqualTo(EMAIL);
    assertThat(customer.getDetails()).isEqualTo(INDIVIDUAL);
    assertThat(customer.getRegisteredAt()).isEqualTo(REGISTERED_AT);
  }

  @Test
  void register_derives_individual_type_from_details() {
    // Act
    Customer customer = Customer.register(ID, NUMBER, EMAIL, INDIVIDUAL, REGISTERED_AT);

    // Assert
    assertThat(customer.getType()).isEqualTo(CustomerType.INDIVIDUAL);
  }

  @Test
  void register_derives_corporate_type_from_details() {
    // Act
    Customer customer = Customer.register(ID, NUMBER, EMAIL, CORPORATE, REGISTERED_AT);

    // Assert
    assertThat(customer.getType()).isEqualTo(CustomerType.CORPORATE);
  }

  @ParameterizedTest
  @MethodSource("oneRequiredFieldNull")
  void rejects_when_a_required_field_is_null(
      CustomerId id,
      CustomerNumber number,
      EmailAddress email,
      CustomerDetails details,
      Instant registeredAt) {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> Customer.register(id, number, email, details, registeredAt));
  }

  private static Stream<Arguments> oneRequiredFieldNull() {
    return Stream.of(
        arguments(null, NUMBER, EMAIL, INDIVIDUAL, REGISTERED_AT),
        arguments(ID, null, EMAIL, INDIVIDUAL, REGISTERED_AT),
        arguments(ID, NUMBER, null, INDIVIDUAL, REGISTERED_AT),
        arguments(ID, NUMBER, EMAIL, null, REGISTERED_AT),
        arguments(ID, NUMBER, EMAIL, INDIVIDUAL, null));
  }
}
