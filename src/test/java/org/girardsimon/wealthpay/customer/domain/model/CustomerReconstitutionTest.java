package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;
import java.util.stream.Stream;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CustomerReconstitutionTest {

  private static final CustomerId ID = CustomerId.of(UUID.randomUUID());
  private static final CustomerNumber NUMBER = CustomerNumber.of("1234567897");
  private static final EmailAddress EMAIL = EmailAddress.of("john.doe@example.com");
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
  void reconstitutes_an_onboarding_customer() {
    // Arrange
    CustomerState state = state(CustomerStatus.ONBOARDING, null);

    // Act
    Customer customer = Customer.reconstitute(state);

    // Assert - identity fields included: this is the sole rebuild path for a persisted aggregate
    assertThat(customer.getId()).isEqualTo(ID);
    assertThat(customer.getNumber()).isEqualTo(NUMBER);
    assertThat(customer.getEmail()).isEqualTo(EMAIL);
    assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ONBOARDING);
    assertThat(customer.getActivatedAt()).isEmpty();
    assertThat(customer.getRegisteredAt()).isEqualTo(REGISTERED_AT);
    assertThat(customer.getDetails()).isEqualTo(INDIVIDUAL);
  }

  @Test
  void reconstitutes_an_active_customer_and_preserves_the_stored_status() {
    // Arrange - unlike register(), reconstitute accepts whatever the row says
    CustomerState state = state(CustomerStatus.ACTIVE, ACTIVATED_AT);

    // Act
    Customer customer = Customer.reconstitute(state);

    // Assert
    assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    assertThat(customer.getActivatedAt()).contains(ACTIVATED_AT);
  }

  @Test
  void reconstituted_active_customer_does_not_transition_again() {
    // Arrange
    Customer customer = Customer.reconstitute(state(CustomerStatus.ACTIVE, ACTIVATED_AT));

    // Act
    boolean transitioned = customer.activate(Instant.parse("2026-07-01T00:00:00Z"));

    // Assert
    assertThat(transitioned).isFalse();
    assertThat(customer.getActivatedAt()).contains(ACTIVATED_AT);
  }

  @ParameterizedTest
  @MethodSource("corruptRows")
  void rejects_a_row_violating_the_active_activation_instant_invariant(
      CustomerStatus status, Instant activatedAt) {
    // Arrange
    CustomerState state = state(status, activatedAt);

    // Act ... Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> Customer.reconstitute(state));
  }

  private static Stream<Arguments> corruptRows() {
    return Stream.of(
        arguments(CustomerStatus.ACTIVE, null), arguments(CustomerStatus.ONBOARDING, ACTIVATED_AT));
  }

  @Test
  void rejects_a_row_activated_before_it_was_registered() {
    // Arrange
    CustomerState state = state(CustomerStatus.ACTIVE, REGISTERED_AT.minusSeconds(1));

    // Act ... Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> Customer.reconstitute(state));
  }

  @Test
  void corruption_message_never_echoes_row_contents() {
    // Arrange
    CustomerState state = state(CustomerStatus.ACTIVE, null);

    // Act ... Assert - the row is PII and this message reaches logs
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> Customer.reconstitute(state))
        .withMessageNotContaining(EMAIL.value())
        .withMessageNotContaining(NUMBER.value())
        .withMessageNotContaining(ID.id().toString());
  }

  @Test
  void rejects_a_null_state() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> Customer.reconstitute(null));
  }

  @ParameterizedTest
  @MethodSource("oneRequiredComponentNull")
  void state_rejects_a_null_required_component(
      CustomerId id,
      CustomerNumber number,
      EmailAddress email,
      CustomerDetails details,
      CustomerStatus status,
      Instant registeredAt) {
    // Act ... Assert - corruption, not a client error: the read side is the only producer, so a
    // missing column must not surface as a 400
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(
            () -> new CustomerState(id, number, email, details, status, registeredAt, null));
  }

  private static Stream<Arguments> oneRequiredComponentNull() {
    return Stream.of(
        arguments(null, NUMBER, EMAIL, INDIVIDUAL, CustomerStatus.ONBOARDING, REGISTERED_AT),
        arguments(ID, null, EMAIL, INDIVIDUAL, CustomerStatus.ONBOARDING, REGISTERED_AT),
        arguments(ID, NUMBER, null, INDIVIDUAL, CustomerStatus.ONBOARDING, REGISTERED_AT),
        arguments(ID, NUMBER, EMAIL, null, CustomerStatus.ONBOARDING, REGISTERED_AT),
        arguments(ID, NUMBER, EMAIL, INDIVIDUAL, null, REGISTERED_AT),
        arguments(ID, NUMBER, EMAIL, INDIVIDUAL, CustomerStatus.ONBOARDING, null));
  }

  private static CustomerState state(CustomerStatus status, Instant activatedAt) {
    return new CustomerState(ID, NUMBER, EMAIL, INDIVIDUAL, status, REGISTERED_AT, activatedAt);
  }
}
