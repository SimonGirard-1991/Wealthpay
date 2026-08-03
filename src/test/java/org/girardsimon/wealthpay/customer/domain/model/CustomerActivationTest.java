package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerActivationTest {

  private static final CustomerId ID = CustomerId.of(UUID.randomUUID());
  private static final CustomerNumber NUMBER = CustomerNumber.of("1234567897");
  private static final EmailAddress EMAIL = EmailAddress.of("john.doe@example.com");
  private static final CustomerDetails INDIVIDUAL =
      new IndividualDetails(
          PersonalName.of("John", "Doe"),
          LocalDate.of(1990, Month.JANUARY, 1),
          Gender.MALE,
          CountryCode.of("FR"));
  private static final Instant ACTIVATED_AT = Instant.parse("2026-06-04T10:15:30Z");
  private static final Instant LATER = Instant.parse("2026-06-05T08:00:00Z");

  @Test
  void activate_moves_an_onboarding_customer_to_active() {
    // Arrange
    Customer customer = Customer.register(ID, NUMBER, EMAIL, INDIVIDUAL);

    // Act
    boolean transitioned = customer.activate(ACTIVATED_AT);

    // Assert
    assertThat(transitioned).isTrue();
    assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    assertThat(customer.getActivatedAt()).contains(ACTIVATED_AT);
  }

  @Test
  void register_leaves_the_activation_instant_empty() {
    // Act
    Customer customer = Customer.register(ID, NUMBER, EMAIL, INDIVIDUAL);

    // Assert
    assertThat(customer.getActivatedAt()).isEmpty();
  }

  @Test
  void activate_reports_no_transition_when_the_customer_is_already_active() {
    // Arrange
    Customer customer = Customer.register(ID, NUMBER, EMAIL, INDIVIDUAL);
    customer.activate(ACTIVATED_AT);

    // Act
    boolean transitioned = customer.activate(LATER);

    // Assert
    assertThat(transitioned).isFalse();
    assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
  }

  @Test
  void activate_keeps_the_first_activation_instant_when_retried() {
    // Arrange
    Customer customer = Customer.register(ID, NUMBER, EMAIL, INDIVIDUAL);
    customer.activate(ACTIVATED_AT);

    // Act
    customer.activate(LATER);

    // Assert
    assertThat(customer.getActivatedAt()).contains(ACTIVATED_AT);
  }

  @Test
  void activate_rejects_a_null_occurrence_instant() {
    // Arrange
    Customer customer = Customer.register(ID, NUMBER, EMAIL, INDIVIDUAL);

    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> customer.activate(null));
  }
}
