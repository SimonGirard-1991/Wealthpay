package org.girardsimon.wealthpay.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.UUID;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.CountryCode;
import org.girardsimon.wealthpay.customer.domain.model.Customer;
import org.girardsimon.wealthpay.customer.domain.model.CustomerDetails;
import org.girardsimon.wealthpay.customer.domain.model.CustomerId;
import org.girardsimon.wealthpay.customer.domain.model.CustomerNumber;
import org.girardsimon.wealthpay.customer.domain.model.CustomerState;
import org.girardsimon.wealthpay.customer.domain.model.CustomerStatus;
import org.girardsimon.wealthpay.customer.domain.model.EmailAddress;
import org.girardsimon.wealthpay.customer.domain.model.Gender;
import org.girardsimon.wealthpay.customer.domain.model.IndividualDetails;
import org.girardsimon.wealthpay.customer.domain.model.Nationalities;
import org.girardsimon.wealthpay.customer.domain.model.PersonalName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivateCustomerTest {

  private static final Instant REGISTERED_AT = Instant.parse("2026-06-04T10:15:30Z");
  private static final Instant NOW = Instant.parse("2026-06-05T08:00:00Z");
  private static final Instant ALREADY_ACTIVATED_AT = Instant.parse("2026-06-05T07:00:00Z");
  private static final Instant WINNER_ACTIVATED_AT = Instant.parse("2026-06-05T07:59:59Z");
  private static final CustomerId CUSTOMER_ID = CustomerId.of(UUID.randomUUID());
  private static final CustomerNumber CUSTOMER_NUMBER = CustomerNumber.of("1234567897");
  private static final EmailAddress EMAIL = EmailAddress.of("john.doe@example.com");
  private static final Actor ACTOR = new Actor("compliance-officer-42");
  private static final CustomerDetails INDIVIDUAL =
      new IndividualDetails(
          PersonalName.of("John", "Doe"),
          LocalDate.of(1990, Month.JANUARY, 1),
          Gender.MALE,
          Nationalities.of(CountryCode.of("FR")),
          CountryCode.of("BE"));

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Mock private CustomerStore customerStore;

  private CustomerApplicationService service;

  @BeforeEach
  void setUp() {
    service = new CustomerApplicationService(customerStore, clock);
  }

  @Test
  void activates_an_onboarding_customer_at_the_clock_instant() {
    // Arrange
    LoadedCustomer loaded = new LoadedCustomer(onboardingCustomer(), 0);
    when(customerStore.load(CUSTOMER_ID)).thenReturn(loaded);
    when(customerStore.recordTransitionAndApply(loaded, CustomerStatus.ACTIVE, NOW, ACTOR))
        .thenReturn(TransitionOutcome.APPLIED);

    // Act
    ActivationResult result = service.activate(CUSTOMER_ID, ACTOR);

    // Assert
    assertThat(result.activatedAt()).isEqualTo(NOW);
    assertThat(result.alreadyActive()).isFalse();
    verify(customerStore).recordTransitionAndApply(loaded, CustomerStatus.ACTIVE, NOW, ACTOR);
    verify(customerStore, never()).findStateAfterSupersededTransition(any());
  }

  @Test
  void reports_an_already_active_customer_without_attempting_a_write() {
    // Arrange
    when(customerStore.load(CUSTOMER_ID)).thenReturn(new LoadedCustomer(activeCustomer(), 1));

    // Act
    ActivationResult result = service.activate(CUSTOMER_ID, ACTOR);

    // Assert
    assertThat(result.activatedAt()).isEqualTo(ALREADY_ACTIVATED_AT);
    assertThat(result.alreadyActive()).isTrue();
    verify(customerStore, never()).recordTransitionAndApply(any(), any(), any(), any());
  }

  @Test
  void reports_the_winner_activation_instant_when_the_transition_is_superseded() {
    // Arrange
    supersededTransitionLeaving(stateOf(CustomerStatus.ACTIVE, WINNER_ACTIVATED_AT));

    // Act
    ActivationResult result = service.activate(CUSTOMER_ID, ACTOR);

    // Assert
    assertThat(result.activatedAt()).isEqualTo(WINNER_ACTIVATED_AT);
    assertThat(result.alreadyActive()).isTrue();
  }

  @Test
  void reports_corruption_when_the_superseded_re_read_is_still_onboarding() {
    // Arrange
    supersededTransitionLeaving(stateOf(CustomerStatus.ONBOARDING, null));

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> service.activate(CUSTOMER_ID, ACTOR));
  }

  @Test
  void reports_corruption_when_the_superseded_re_read_carries_no_activation_instant() {
    // Arrange
    supersededTransitionLeaving(stateOf(CustomerStatus.ACTIVE, null));

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> service.activate(CUSTOMER_ID, ACTOR));
  }

  private void supersededTransitionLeaving(CustomerState fresh) {
    LoadedCustomer loaded = new LoadedCustomer(onboardingCustomer(), 0);
    when(customerStore.load(CUSTOMER_ID)).thenReturn(loaded);
    when(customerStore.recordTransitionAndApply(loaded, CustomerStatus.ACTIVE, NOW, ACTOR))
        .thenReturn(TransitionOutcome.SUPERSEDED);
    when(customerStore.findStateAfterSupersededTransition(CUSTOMER_ID)).thenReturn(fresh);
  }

  private static Customer onboardingCustomer() {
    return Customer.register(CUSTOMER_ID, CUSTOMER_NUMBER, EMAIL, INDIVIDUAL, REGISTERED_AT);
  }

  private static Customer activeCustomer() {
    Customer customer = onboardingCustomer();
    customer.activate(ALREADY_ACTIVATED_AT);
    return customer;
  }

  private static CustomerState stateOf(CustomerStatus status, Instant activatedAt) {
    return new CustomerState(
        CUSTOMER_ID, CUSTOMER_NUMBER, EMAIL, INDIVIDUAL, status, REGISTERED_AT, activatedAt);
  }
}
