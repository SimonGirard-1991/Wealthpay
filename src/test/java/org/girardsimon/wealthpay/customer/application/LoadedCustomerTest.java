package org.girardsimon.wealthpay.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.CountryCode;
import org.girardsimon.wealthpay.customer.domain.model.Customer;
import org.girardsimon.wealthpay.customer.domain.model.CustomerDetails;
import org.girardsimon.wealthpay.customer.domain.model.CustomerId;
import org.girardsimon.wealthpay.customer.domain.model.CustomerNumber;
import org.girardsimon.wealthpay.customer.domain.model.CustomerStatus;
import org.girardsimon.wealthpay.customer.domain.model.EmailAddress;
import org.girardsimon.wealthpay.customer.domain.model.Gender;
import org.girardsimon.wealthpay.customer.domain.model.IndividualDetails;
import org.girardsimon.wealthpay.customer.domain.model.Nationalities;
import org.girardsimon.wealthpay.customer.domain.model.PersonalName;
import org.junit.jupiter.api.Test;

class LoadedCustomerTest {

  private static final Instant REGISTERED_AT = Instant.parse("2026-06-04T10:15:30Z");
  private static final Instant ACTIVATED_AT = Instant.parse("2026-06-05T08:00:00Z");
  private static final CustomerDetails INDIVIDUAL =
      new IndividualDetails(
          PersonalName.of("John", "Doe"),
          LocalDate.of(1990, Month.JANUARY, 1),
          Gender.MALE,
          Nationalities.of(CountryCode.of("FR")),
          CountryCode.of("BE"));

  @Test
  void carries_the_aggregate_and_its_transition_sequence() {
    // Arrange - zero is the sequence of a customer that has never transitioned
    Customer customer = onboardingCustomer();

    // Act
    LoadedCustomer loaded = new LoadedCustomer(customer, 0);

    // Assert
    assertThat(loaded.customer()).isSameAs(customer);
    assertThat(loaded.sequenceNo()).isZero();
  }

  @Test
  void accepts_an_active_customer_that_has_a_transition() {
    // Arrange
    Customer customer = activeCustomer();

    // Act
    LoadedCustomer loaded = new LoadedCustomer(customer, 1);

    // Assert
    assertThat(loaded.sequenceNo()).isEqualTo(1);
  }

  @Test
  void rejects_a_missing_aggregate() {
    // Act ... Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> new LoadedCustomer(null, 0));
  }

  @Test
  void rejects_a_negative_transition_sequence() {
    // Arrange
    Customer customer = onboardingCustomer();

    // Act ... Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> new LoadedCustomer(customer, -1));
  }

  @Test
  void rejects_an_active_customer_with_no_transition() {
    // Arrange - only a direct UPDATE bypassing the audit write could produce this row
    Customer customer = activeCustomer();

    // Act ... Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> new LoadedCustomer(customer, 0));
  }

  @Test
  void rejects_an_onboarding_customer_that_carries_a_transition() {
    // Arrange - ONBOARDING is the initial state only, so nothing ever transitions into it
    Customer customer = onboardingCustomer();

    // Act ... Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> new LoadedCustomer(customer, 1));
  }

  @Test
  void builds_a_transition_whose_source_comes_from_the_loaded_snapshot() {
    // Arrange
    LoadedCustomer loaded = new LoadedCustomer(onboardingCustomer(), 0);

    // Act
    StatusTransition transition = loaded.transitionTo(CustomerStatus.ACTIVE);

    // Assert
    assertThat(transition.from()).isEqualTo(CustomerStatus.ONBOARDING);
    assertThat(transition.to()).isEqualTo(CustomerStatus.ACTIVE);
  }

  @Test
  void keeps_reporting_the_loaded_status_after_the_aggregate_is_activated() {
    // Arrange
    Customer customer = onboardingCustomer();
    LoadedCustomer loaded = new LoadedCustomer(customer, 0);

    // Act - the use case activates the aggregate before the write is issued
    customer.activate(ACTIVATED_AT);

    // Assert - the transition still describes the row that was read, not the outcome attempted
    StatusTransition transition = loaded.transitionTo(CustomerStatus.ACTIVE);
    assertThat(transition.from()).isEqualTo(CustomerStatus.ONBOARDING);
    assertThat(transition.to()).isEqualTo(CustomerStatus.ACTIVE);
  }

  @Test
  void refuses_to_build_a_transition_to_the_status_already_held() {
    // Arrange
    LoadedCustomer loaded = new LoadedCustomer(activeCustomer(), 1);

    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> loaded.transitionTo(CustomerStatus.ACTIVE));
  }

  private static Customer onboardingCustomer() {
    return Customer.register(
        CustomerId.of(UUID.randomUUID()),
        CustomerNumber.of("1234567897"),
        EmailAddress.of("john.doe@example.com"),
        INDIVIDUAL,
        REGISTERED_AT);
  }

  private static Customer activeCustomer() {
    Customer customer = onboardingCustomer();
    customer.activate(ACTIVATED_AT);
    return customer;
  }
}
