package org.girardsimon.wealthpay.customer.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.girardsimon.wealthpay.customer.infrastructure.db.repository.CustomerFixtures.mintCustomerNumber;
import static org.girardsimon.wealthpay.customer.infrastructure.db.repository.CustomerFixtures.mintEmail;
import static org.girardsimon.wealthpay.customer.jooq.tables.AdmissionDecision.ADMISSION_DECISION;
import static org.girardsimon.wealthpay.customer.jooq.tables.CustomerAdmission.CUSTOMER_ADMISSION;
import static org.girardsimon.wealthpay.customer.jooq.tables.CustomerStatusTransition.CUSTOMER_STATUS_TRANSITION;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.girardsimon.wealthpay.customer.application.Actor;
import org.girardsimon.wealthpay.customer.application.AdmissionDecisionId;
import org.girardsimon.wealthpay.customer.application.CustomerNotFoundException;
import org.girardsimon.wealthpay.customer.application.CustomerNumberCollisionException;
import org.girardsimon.wealthpay.customer.application.CustomerStore;
import org.girardsimon.wealthpay.customer.application.EmailAlreadyRegisteredException;
import org.girardsimon.wealthpay.customer.application.LoadedCustomer;
import org.girardsimon.wealthpay.customer.application.TransitionOutcome;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.CorporateDetails;
import org.girardsimon.wealthpay.customer.domain.model.CountryCode;
import org.girardsimon.wealthpay.customer.domain.model.Customer;
import org.girardsimon.wealthpay.customer.domain.model.CustomerDetails;
import org.girardsimon.wealthpay.customer.domain.model.CustomerId;
import org.girardsimon.wealthpay.customer.domain.model.CustomerNumber;
import org.girardsimon.wealthpay.customer.domain.model.CustomerState;
import org.girardsimon.wealthpay.customer.domain.model.CustomerStatus;
import org.girardsimon.wealthpay.customer.domain.model.CustomerType;
import org.girardsimon.wealthpay.customer.domain.model.EmailAddress;
import org.girardsimon.wealthpay.customer.domain.model.Gender;
import org.girardsimon.wealthpay.customer.domain.model.IndividualDetails;
import org.girardsimon.wealthpay.customer.domain.model.Nationalities;
import org.girardsimon.wealthpay.customer.domain.model.PersonalName;
import org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper.CustomerRowToStateMapper;
import org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper.CustomerToRowMapper;
import org.girardsimon.wealthpay.shared.config.TimeConfig;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.Record;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import({
  CustomerRepository.class,
  CustomerToRowMapper.class,
  CustomerRowToStateMapper.class,
  TimeConfig.class
})
class CustomerRepositoryTest extends AbstractCustomerContainerTest {

  private static final Instant REGISTERED_AT = Instant.parse("2026-06-01T09:00:00Z");
  private static final Instant ACTIVATED_AT = Instant.parse("2026-06-04T10:15:30Z");
  private static final CustomerDetails INDIVIDUAL =
      new IndividualDetails(
          PersonalName.of("Ada", "Augusta", "Lovelace"),
          LocalDate.of(1990, Month.JANUARY, 1),
          Gender.FEMALE,
          Nationalities.of(CountryCode.of("FR"), CountryCode.of("GB")),
          CountryCode.of("FR"));
  private static final CustomerDetails CORPORATE =
      new CorporateDetails("Acme SA", "RCS-123", CountryCode.of("FR"));

  @Autowired private CustomerStore customerStore;

  @Autowired private DSLContext dslContext;

  @Test
  void an_individual_round_trips_with_every_nationality_it_holds() {
    // Arrange
    Customer customer = register(INDIVIDUAL);

    // Act
    Customer loaded = insertAndLoad(customer).customer();

    // Assert
    assertAll(
        () -> assertThat(loaded.getNumber()).isEqualTo(customer.getNumber()),
        () -> assertThat(loaded.getNumber().value()).startsWith("0"),
        () -> assertThat(loaded.getEmail()).isEqualTo(customer.getEmail()),
        () -> assertThat(loaded.getRegisteredAt()).isEqualTo(REGISTERED_AT),
        () -> assertThat(loaded.getDetails()).isEqualTo(INDIVIDUAL));
  }

  @Test
  void an_individual_without_a_middle_name_round_trips() {
    // Arrange
    CustomerDetails details =
        new IndividualDetails(
            PersonalName.of("Alan", "Turing"),
            LocalDate.of(1912, Month.JUNE, 23),
            Gender.MALE,
            Nationalities.of(CountryCode.of("GB")),
            CountryCode.of("GB"));

    // Act
    Customer loaded = insertAndLoad(register(details)).customer();

    // Assert
    assertThat(loaded.getDetails()).isEqualTo(details);
  }

  @Test
  void a_corporate_round_trips() {
    // Act
    Customer loaded = insertAndLoad(register(CORPORATE)).customer();

    // Assert
    assertAll(
        () -> assertThat(loaded.getType()).isEqualTo(CustomerType.CORPORATE),
        () -> assertThat(loaded.getDetails()).isEqualTo(CORPORATE));
  }

  @Test
  void a_registered_customer_is_onboarding_and_holds_no_transition() {
    // Act
    LoadedCustomer loaded = insertAndLoad(register(INDIVIDUAL));

    // Assert
    assertAll(
        () -> assertThat(loaded.customer().getStatus()).isEqualTo(CustomerStatus.ONBOARDING),
        () -> assertThat(loaded.customer().getActivatedAt()).isEmpty(),
        () -> assertThat(loaded.sequenceNo()).isZero());
  }

  @Test
  void load_rejects_an_unknown_customer() {
    // Arrange
    CustomerId unknown = CustomerId.of(UUID.randomUUID());

    // Act + Assert
    assertThatExceptionOfType(CustomerNotFoundException.class)
        .isThrownBy(() -> customerStore.load(unknown));
  }

  @Test
  void the_superseded_re_read_reports_an_absent_row_as_corruption() {
    // Arrange
    CustomerId unknown = CustomerId.of(UUID.randomUUID());

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> customerStore.findStateAfterSupersededTransition(unknown));
  }

  @Test
  void an_admitted_customer_is_anchored_to_the_decision_that_admitted_it() {
    // Arrange
    Customer customer = register(INDIVIDUAL);
    AdmissionDecisionId decisionId = admittedDecision(CustomerType.INDIVIDUAL);

    // Act
    customerStore.insert(customer, decisionId);

    // Assert
    assertThat(linkCount(customer.getId(), decisionId)).isOne();
  }

  @Test
  void activation_writes_the_transition_and_applies_the_status_in_one_statement() {
    // Arrange
    LoadedCustomer loaded = insertAndLoad(register(INDIVIDUAL));
    loaded.customer().activate(ACTIVATED_AT);

    // Act
    TransitionOutcome outcome =
        customerStore.recordTransitionAndApply(
            loaded, CustomerStatus.ACTIVE, ACTIVATED_AT, Actor.SYSTEM);

    // Assert
    Record transition = onlyTransition(loaded.customer().getId());
    assertAll(
        () -> assertThat(outcome).isEqualTo(TransitionOutcome.APPLIED),
        () -> assertThat(transition.get(CUSTOMER_STATUS_TRANSITION.SEQUENCE_NO)).isOne(),
        () ->
            assertThat(transition.get(CUSTOMER_STATUS_TRANSITION.FROM_STATUS))
                .isEqualTo("ONBOARDING"),
        () -> assertThat(transition.get(CUSTOMER_STATUS_TRANSITION.TO_STATUS)).isEqualTo("ACTIVE"),
        () -> assertThat(transition.get(CUSTOMER_STATUS_TRANSITION.ACTOR)).isEqualTo("SYSTEM"),
        () ->
            assertThat(transition.get(CUSTOMER_STATUS_TRANSITION.OCCURRED_AT).toInstant())
                .isEqualTo(ACTIVATED_AT));
  }

  @Test
  void an_activated_customer_reloads_as_active_at_the_first_sequence() {
    // Arrange
    LoadedCustomer loaded = insertAndLoad(register(INDIVIDUAL));
    activate(loaded.customer().getId());

    // Act
    LoadedCustomer reloaded = customerStore.load(loaded.customer().getId());

    // Assert
    assertAll(
        () -> assertThat(reloaded.customer().getStatus()).isEqualTo(CustomerStatus.ACTIVE),
        () -> assertThat(reloaded.customer().getActivatedAt()).contains(ACTIVATED_AT),
        () -> assertThat(reloaded.sequenceNo()).isOne());
  }

  @Test
  void a_snapshot_whose_sequence_another_caller_already_took_is_superseded() {
    // Arrange
    LoadedCustomer loaded = insertAndLoad(register(INDIVIDUAL));
    activate(loaded.customer().getId());

    // Act
    TransitionOutcome outcome =
        customerStore.recordTransitionAndApply(
            loaded, CustomerStatus.ACTIVE, Instant.parse("2026-06-05T08:00:00Z"), Actor.SYSTEM);

    // Assert
    assertAll(
        () -> assertThat(outcome).isEqualTo(TransitionOutcome.SUPERSEDED),
        () -> assertThat(transitionCount(loaded.customer().getId())).isOne());
  }

  @Test
  void a_superseded_caller_leaves_the_winner_activation_instant_untouched() {
    // Arrange
    LoadedCustomer loaded = insertAndLoad(register(INDIVIDUAL));
    activate(loaded.customer().getId());
    customerStore.recordTransitionAndApply(
        loaded, CustomerStatus.ACTIVE, Instant.parse("2026-06-05T08:00:00Z"), Actor.SYSTEM);

    // Act
    CustomerState state =
        customerStore.findStateAfterSupersededTransition(loaded.customer().getId());

    // Assert
    assertAll(
        () -> assertThat(state.status()).isEqualTo(CustomerStatus.ACTIVE),
        () -> assertThat(state.activatedAt()).isEqualTo(ACTIVATED_AT));
  }

  @Test
  void onboarding_is_refused_as_a_transition_target() {
    // Arrange
    LoadedCustomer loaded = insertAndLoad(register(INDIVIDUAL));
    activate(loaded.customer().getId());
    LoadedCustomer active = customerStore.load(loaded.customer().getId());

    // Act + Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(
            () ->
                customerStore.recordTransitionAndApply(
                    active, CustomerStatus.ONBOARDING, ACTIVATED_AT, Actor.SYSTEM));
  }

  @Test
  void a_taken_email_is_reported_as_client_fixable() {
    // Arrange
    Customer first = insertAndLoad(register(INDIVIDUAL)).customer();
    Customer other = register(mintCustomerNumber(), first.getEmail().value(), INDIVIDUAL);
    AdmissionDecisionId decisionId = admittedDecision(CustomerType.INDIVIDUAL);

    // Act + Assert
    assertThatExceptionOfType(EmailAlreadyRegisteredException.class)
        .isThrownBy(() -> customerStore.insert(other, decisionId));
  }

  @Test
  void a_taken_customer_number_is_reported_as_a_minted_identifier_collision() {
    // Arrange
    Customer first = insertAndLoad(register(INDIVIDUAL)).customer();
    Customer other = register(first.getNumber().value(), mintEmail(), INDIVIDUAL);
    AdmissionDecisionId decisionId = admittedDecision(CustomerType.INDIVIDUAL);

    // Act + Assert
    assertThatExceptionOfType(CustomerNumberCollisionException.class)
        .isThrownBy(() -> customerStore.insert(other, decisionId));
  }

  @Test
  void anchoring_the_same_admission_again_is_a_no_op() {
    // Arrange
    Customer customer = register(INDIVIDUAL);
    AdmissionDecisionId decisionId = admittedDecision(CustomerType.INDIVIDUAL);
    customerStore.insert(customer, decisionId);

    // Act
    customerStore.linkAdmission(customer.getId(), decisionId);

    // Assert
    assertThat(linkCount(customer.getId(), decisionId)).isOne();
  }

  @Test
  void anchoring_an_admission_to_an_absent_customer_is_reported_as_corruption() {
    // Arrange
    AdmissionDecisionId decisionId = admittedDecision(CustomerType.INDIVIDUAL);
    CustomerId absent = CustomerId.of(UUID.randomUUID());

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> customerStore.linkAdmission(absent, decisionId));
  }

  private LoadedCustomer insertAndLoad(Customer customer) {
    customerStore.insert(customer, admittedDecision(customer.getType()));
    return customerStore.load(customer.getId());
  }

  /** Activates through a snapshot of its own, so the caller's stays stale. */
  private void activate(CustomerId customerId) {
    LoadedCustomer winner = customerStore.load(customerId);
    winner.customer().activate(ACTIVATED_AT);
    customerStore.recordTransitionAndApply(
        winner, CustomerStatus.ACTIVE, ACTIVATED_AT, Actor.SYSTEM);
  }

  private static Customer register(CustomerDetails details) {
    return register(mintCustomerNumber(), mintEmail(), details);
  }

  private static Customer register(String number, String email, CustomerDetails details) {
    return Customer.register(
        CustomerId.of(UUID.randomUUID()),
        CustomerNumber.of(number),
        EmailAddress.of(email),
        details,
        REGISTERED_AT);
  }

  /** No recorder exists yet, so the decision a link needs is seeded through jOOQ directly. */
  private AdmissionDecisionId admittedDecision(CustomerType subjectType) {
    UUID id = UUID.randomUUID();
    InsertSetMoreStep<?> insert =
        dslContext
            .insertInto(ADMISSION_DECISION)
            .set(ADMISSION_DECISION.ID, id)
            .set(ADMISSION_DECISION.IDEMPOTENCY_KEY, id.toString())
            .set(ADMISSION_DECISION.POLICY_VERSION, 1L)
            .set(ADMISSION_DECISION.OUTCOME, "ADMITTED")
            .set(ADMISSION_DECISION.SUBJECT_TYPE, subjectType.name())
            .set(ADMISSION_DECISION.DECIDED_AT, OffsetDateTime.parse("2026-06-01T09:00:00Z"));
    switch (subjectType) {
      case INDIVIDUAL ->
          insert
              .set(ADMISSION_DECISION.NATIONALITIES, new String[] {"FR"})
              .set(ADMISSION_DECISION.COUNTRY_OF_RESIDENCE, "FR");
      case CORPORATE -> insert.set(ADMISSION_DECISION.COUNTRY_OF_INCORPORATION, "FR");
    }
    insert.execute();
    return new AdmissionDecisionId(id);
  }

  private int linkCount(CustomerId customerId, AdmissionDecisionId decisionId) {
    return dslContext.fetchCount(
        CUSTOMER_ADMISSION,
        CUSTOMER_ADMISSION
            .CUSTOMER_ID
            .eq(customerId.id())
            .and(CUSTOMER_ADMISSION.DECISION_ID.eq(decisionId.id())));
  }

  private int transitionCount(CustomerId customerId) {
    return dslContext.fetchCount(
        CUSTOMER_STATUS_TRANSITION, CUSTOMER_STATUS_TRANSITION.CUSTOMER_ID.eq(customerId.id()));
  }

  private Record onlyTransition(CustomerId customerId) {
    return dslContext
        .selectFrom(CUSTOMER_STATUS_TRANSITION)
        .where(CUSTOMER_STATUS_TRANSITION.CUSTOMER_ID.eq(customerId.id()))
        .fetchSingle();
  }
}
