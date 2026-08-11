package org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.CorporateDetails;
import org.girardsimon.wealthpay.customer.domain.model.CountryCode;
import org.girardsimon.wealthpay.customer.domain.model.CustomerState;
import org.girardsimon.wealthpay.customer.domain.model.CustomerStatus;
import org.girardsimon.wealthpay.customer.domain.model.Gender;
import org.girardsimon.wealthpay.customer.domain.model.IndividualDetails;
import org.girardsimon.wealthpay.customer.jooq.tables.records.CustomerRecord;
import org.junit.jupiter.api.Test;

/** The rejection tests assert the exception type: each domain type would raise one of its own. */
class CustomerRowToStateMapperTest {

  private static final UUID ID = UUID.randomUUID();
  private static final OffsetDateTime REGISTERED_AT = OffsetDateTime.parse("2026-06-01T09:00:00Z");
  private static final OffsetDateTime ACTIVATED_AT = OffsetDateTime.parse("2026-06-04T10:15:30Z");
  private static final String[] TWO_NATIONALITIES = {"FR", "GB"};

  private final CustomerRowToStateMapper mapper = new CustomerRowToStateMapper();

  @Test
  void maps_an_individual_row_with_every_nationality_it_carries() {
    // Arrange
    CustomerRecord row = individualRow();

    // Act
    CustomerState state = mapper.toCustomerState(row, TWO_NATIONALITIES);

    // Assert
    IndividualDetails details = (IndividualDetails) state.details();
    assertThat(details.nationalities().values())
        .containsExactlyInAnyOrder(CountryCode.of("FR"), CountryCode.of("GB"));
    assertThat(details.name().givenName()).isEqualTo("Ada");
    assertThat(details.dateOfBirth()).isEqualTo(LocalDate.of(1990, Month.JANUARY, 1));
    assertThat(details.gender()).isEqualTo(Gender.FEMALE);
    assertThat(details.countryOfResidence()).isEqualTo(CountryCode.of("FR"));
  }

  @Test
  void maps_the_identity_columns_shared_by_both_customer_kinds() {
    // Arrange
    CustomerRecord row = individualRow();

    // Act
    CustomerState state = mapper.toCustomerState(row, TWO_NATIONALITIES);

    // Assert
    assertThat(state.id().id()).isEqualTo(ID);
    assertThat(state.number().value()).isEqualTo("0000000018");
    assertThat(state.email().value()).isEqualTo("ada@example.com");
    assertThat(state.status()).isEqualTo(CustomerStatus.ONBOARDING);
    assertThat(state.registeredAt()).isEqualTo(Instant.parse("2026-06-01T09:00:00Z"));
  }

  @Test
  void maps_a_corporate_row() {
    // Arrange
    CustomerRecord row = corporateRow();

    // Act
    CustomerState state = mapper.toCustomerState(row, null);

    // Assert
    assertThat(state.details())
        .isEqualTo(new CorporateDetails("Acme SA", "RCS-123", CountryCode.of("FR")));
  }

  @Test
  void maps_an_individual_row_without_a_middle_name() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setMiddleName(null);

    // Act
    CustomerState state = mapper.toCustomerState(row, TWO_NATIONALITIES);

    // Assert
    assertThat(((IndividualDetails) state.details()).name().middleName()).isNull();
  }

  @Test
  void leaves_the_activation_instant_null_while_the_customer_is_onboarding() {
    // Act
    CustomerState state = mapper.toCustomerState(individualRow(), TWO_NATIONALITIES);

    // Assert
    assertThat(state.activatedAt()).isNull();
  }

  @Test
  void maps_the_activation_instant_of_an_active_row() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setStatus("ACTIVE");
    row.setActivatedAt(ACTIVATED_AT);

    // Act
    CustomerState state = mapper.toCustomerState(row, TWO_NATIONALITIES);

    // Assert
    assertThat(state.activatedAt()).isEqualTo(Instant.parse("2026-06-04T10:15:30Z"));
  }

  @Test
  void reports_an_unknown_status_as_a_corrupt_row() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setStatus("SUSPENDED");

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(row, TWO_NATIONALITIES));
  }

  @Test
  void reports_an_unknown_kind_as_a_corrupt_row() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setKind("TRUST");

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(row, TWO_NATIONALITIES));
  }

  @Test
  void reports_an_unknown_gender_as_a_corrupt_row() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setGender("OTHER");

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(row, TWO_NATIONALITIES));
  }

  @Test
  void reports_an_individual_carrying_no_nationality_as_a_corrupt_row() {
    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(individualRow(), new String[0]));
  }

  @Test
  void reports_an_individual_whose_nationalities_are_absent_as_a_corrupt_row() {
    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(individualRow(), null));
  }

  @Test
  void reports_a_customer_number_failing_its_check_digit_as_a_corrupt_row() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setCustomerNumber("0000000019");

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(row, TWO_NATIONALITIES));
  }

  @Test
  void reports_a_residence_outside_iso_3166_as_a_corrupt_row() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setCountryOfResidence("ZZ");

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(row, TWO_NATIONALITIES));
  }

  @Test
  void reports_a_nationality_outside_iso_3166_as_a_corrupt_row() {
    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(individualRow(), new String[] {"ZZ"}));
  }

  @Test
  void reports_a_malformed_email_as_a_corrupt_row() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setEmail("not-an-address");

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(row, TWO_NATIONALITIES));
  }

  @Test
  void names_the_rejected_column_without_quoting_its_value() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setEmail("ada@@example@com");

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(row, TWO_NATIONALITIES))
        .withMessage("Customer row carries an invalid email")
        .satisfies(e -> assertThat(e.getMessage()).doesNotContain("ada"));
  }

  @Test
  void chains_no_cause_that_would_carry_the_value_into_the_logs() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setCustomerNumber("0000000019");

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(row, TWO_NATIONALITIES))
        .withMessage("Customer row carries an invalid customer_number")
        .satisfies(e -> assertThat(e.getCause()).isNull());
  }

  @Test
  void keeps_the_precise_message_when_a_nested_column_is_the_one_at_fault() {
    // Arrange
    String[] withNullElement = {"FR", null};

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(individualRow(), withNullElement))
        .withMessage("Customer row is missing country_code");
  }

  @Test
  void reports_a_blank_family_name_as_a_corrupt_row() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setFamilyName("   ");

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(row, TWO_NATIONALITIES));
  }

  @Test
  void reports_a_blank_registered_name_as_a_corrupt_row() {
    // Arrange
    CustomerRecord row = corporateRow();
    row.setRegisteredName("   ");

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(row, null));
  }

  @Test
  void reports_more_nationalities_than_the_domain_allows_as_a_corrupt_row() {
    // Arrange
    String[] eleven = {"FR", "GB", "DE", "IT", "ES", "PT", "NL", "BE", "AT", "CH", "SE"};

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(individualRow(), eleven));
  }

  @Test
  void reports_a_missing_mandatory_column_as_a_corrupt_row() {
    // Arrange
    CustomerRecord row = individualRow();
    row.setFamilyName(null);

    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(row, TWO_NATIONALITIES));
  }

  @Test
  void reports_an_absent_row_as_a_corrupt_row() {
    // Act + Assert
    assertThatExceptionOfType(CustomerRowCorruptException.class)
        .isThrownBy(() -> mapper.toCustomerState(null, TWO_NATIONALITIES));
  }

  private static CustomerRecord individualRow() {
    CustomerRecord row = sharedColumns("INDIVIDUAL");
    row.setGivenName("Ada");
    row.setMiddleName("Augusta");
    row.setFamilyName("Lovelace");
    row.setDateOfBirth(LocalDate.of(1990, Month.JANUARY, 1));
    row.setGender("FEMALE");
    row.setCountryOfResidence("FR");
    return row;
  }

  private static CustomerRecord corporateRow() {
    CustomerRecord row = sharedColumns("CORPORATE");
    row.setRegisteredName("Acme SA");
    row.setRegistrationNumber("RCS-123");
    row.setCountryOfIncorporation("FR");
    return row;
  }

  private static CustomerRecord sharedColumns(String kind) {
    CustomerRecord row = new CustomerRecord();
    row.setId(ID);
    // Luhn-valid and carrying leading zeros, which is the shape the generator emits.
    row.setCustomerNumber("0000000018");
    row.setEmail("ada@example.com");
    row.setStatus("ONBOARDING");
    row.setKind(kind);
    row.setRegisteredAt(REGISTERED_AT);
    return row;
  }
}
