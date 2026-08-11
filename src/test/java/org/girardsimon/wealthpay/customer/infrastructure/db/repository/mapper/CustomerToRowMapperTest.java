package org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.girardsimon.wealthpay.customer.domain.model.CorporateDetails;
import org.girardsimon.wealthpay.customer.domain.model.CountryCode;
import org.girardsimon.wealthpay.customer.domain.model.Customer;
import org.girardsimon.wealthpay.customer.domain.model.CustomerDetails;
import org.girardsimon.wealthpay.customer.domain.model.CustomerId;
import org.girardsimon.wealthpay.customer.domain.model.CustomerNumber;
import org.girardsimon.wealthpay.customer.domain.model.EmailAddress;
import org.girardsimon.wealthpay.customer.domain.model.Gender;
import org.girardsimon.wealthpay.customer.domain.model.IndividualDetails;
import org.girardsimon.wealthpay.customer.domain.model.Nationalities;
import org.girardsimon.wealthpay.customer.domain.model.PersonalName;
import org.girardsimon.wealthpay.customer.jooq.tables.records.CustomerNationalityRecord;
import org.girardsimon.wealthpay.customer.jooq.tables.records.CustomerRecord;
import org.junit.jupiter.api.Test;

class CustomerToRowMapperTest {

  private static final CustomerId ID = CustomerId.of(UUID.randomUUID());
  private static final CustomerNumber NUMBER = CustomerNumber.of("0000000018");
  private static final EmailAddress EMAIL = EmailAddress.of("ada@example.com");
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

  private final CustomerToRowMapper mapper =
      new CustomerToRowMapper(Clock.fixed(REGISTERED_AT, ZoneOffset.UTC));

  @Test
  void an_individual_populates_the_individual_columns() {
    // Act
    CustomerRecord row = mapper.toCustomerRow(customer(INDIVIDUAL));

    // Assert
    assertAll(
        () -> assertThat(row.getKind()).isEqualTo("INDIVIDUAL"),
        () -> assertThat(row.getGivenName()).isEqualTo("Ada"),
        () -> assertThat(row.getMiddleName()).isEqualTo("Augusta"),
        () -> assertThat(row.getFamilyName()).isEqualTo("Lovelace"),
        () -> assertThat(row.getDateOfBirth()).isEqualTo(LocalDate.of(1990, Month.JANUARY, 1)),
        () -> assertThat(row.getGender()).isEqualTo("FEMALE"),
        () -> assertThat(row.getCountryOfResidence()).isEqualTo("FR"));
  }

  @Test
  void the_identity_columns_are_taken_from_the_customer() {
    // Act
    CustomerRecord row = mapper.toCustomerRow(customer(INDIVIDUAL));

    // Assert
    assertAll(
        () -> assertThat(row.getId()).isEqualTo(ID.id()),
        () -> assertThat(row.getCustomerNumber()).isEqualTo("0000000018"),
        () -> assertThat(row.getEmail()).isEqualTo("ada@example.com"));
  }

  @Test
  void an_individual_leaves_the_corporate_columns_null() {
    // Act
    CustomerRecord row = mapper.toCustomerRow(customer(INDIVIDUAL));

    // Assert
    assertAll(
        () -> assertThat(row.getRegisteredName()).isNull(),
        () -> assertThat(row.getRegistrationNumber()).isNull(),
        () -> assertThat(row.getCountryOfIncorporation()).isNull());
  }

  @Test
  void a_corporate_populates_the_corporate_columns() {
    // Act
    CustomerRecord row = mapper.toCustomerRow(customer(CORPORATE));

    // Assert
    assertAll(
        () -> assertThat(row.getKind()).isEqualTo("CORPORATE"),
        () -> assertThat(row.getRegisteredName()).isEqualTo("Acme SA"),
        () -> assertThat(row.getRegistrationNumber()).isEqualTo("RCS-123"),
        () -> assertThat(row.getCountryOfIncorporation()).isEqualTo("FR"));
  }

  @Test
  void a_corporate_leaves_the_individual_columns_null() {
    // Act
    CustomerRecord row = mapper.toCustomerRow(customer(CORPORATE));

    // Assert
    assertAll(
        () -> assertThat(row.getGivenName()).isNull(),
        () -> assertThat(row.getFamilyName()).isNull(),
        () -> assertThat(row.getDateOfBirth()).isNull(),
        () -> assertThat(row.getGender()).isNull(),
        () -> assertThat(row.getCountryOfResidence()).isNull());
  }

  @Test
  void an_onboarding_customer_carries_no_activation_instant() {
    // Act
    CustomerRecord row = mapper.toCustomerRow(customer(INDIVIDUAL));

    // Assert
    assertAll(
        () -> assertThat(row.getStatus()).isEqualTo("ONBOARDING"),
        () -> assertThat(row.getActivatedAt()).isNull(),
        () -> assertThat(row.getRegisteredAt().toInstant()).isEqualTo(REGISTERED_AT));
  }

  @Test
  void an_active_customer_carries_its_activation_instant() {
    // Arrange
    Customer customer = customer(INDIVIDUAL);
    customer.activate(ACTIVATED_AT);

    // Act
    CustomerRecord row = mapper.toCustomerRow(customer);

    // Assert
    assertAll(
        () -> assertThat(row.getStatus()).isEqualTo("ACTIVE"),
        () -> assertThat(row.getActivatedAt().toInstant()).isEqualTo(ACTIVATED_AT));
  }

  @Test
  void a_nationality_row_is_produced_per_country_and_carries_the_parent_kind() {
    // Act
    List<CustomerNationalityRecord> rows = mapper.toNationalityRows(customer(INDIVIDUAL));

    // Assert
    assertThat(rows)
        .allSatisfy(
            row -> {
              assertThat(row.getCustomerId()).isEqualTo(ID.id());
              assertThat(row.getKind()).isEqualTo("INDIVIDUAL");
            })
        .extracting(CustomerNationalityRecord::getCountryCode)
        .containsExactlyInAnyOrder("FR", "GB");
  }

  @Test
  void a_corporate_produces_no_nationality_row() {
    // Act
    List<CustomerNationalityRecord> rows = mapper.toNationalityRows(customer(CORPORATE));

    // Assert
    assertThat(rows).isEmpty();
  }

  private static Customer customer(CustomerDetails details) {
    return Customer.register(ID, NUMBER, EMAIL, details, REGISTERED_AT);
  }
}
