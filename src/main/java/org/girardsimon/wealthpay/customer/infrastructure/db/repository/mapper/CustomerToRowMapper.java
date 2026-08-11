package org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.girardsimon.wealthpay.customer.domain.model.CorporateDetails;
import org.girardsimon.wealthpay.customer.domain.model.CountryCode;
import org.girardsimon.wealthpay.customer.domain.model.Customer;
import org.girardsimon.wealthpay.customer.domain.model.IndividualDetails;
import org.girardsimon.wealthpay.customer.domain.model.PersonalName;
import org.girardsimon.wealthpay.customer.jooq.tables.records.CustomerNationalityRecord;
import org.girardsimon.wealthpay.customer.jooq.tables.records.CustomerRecord;
import org.springframework.stereotype.Component;

@Component
public class CustomerToRowMapper {

  private final Clock clock;

  public CustomerToRowMapper(Clock clock) {
    this.clock = clock;
  }

  public CustomerRecord toCustomerRow(Customer customer) {
    CustomerRecord row = new CustomerRecord();
    row.setId(customer.getId().id());
    row.setCustomerNumber(customer.getNumber().value());
    row.setEmail(customer.getEmail().value());
    row.setStatus(customer.getStatus().name());
    row.setKind(customer.getType().name());
    row.setRegisteredAt(timestamp(customer.getRegisteredAt()));
    customer.getActivatedAt().ifPresent(activatedAt -> row.setActivatedAt(timestamp(activatedAt)));
    switch (customer.getDetails()) {
      case IndividualDetails individual -> setIndividualColumns(row, individual);
      case CorporateDetails corporate -> setCorporateColumns(row, corporate);
    }
    return row;
  }

  public List<CustomerNationalityRecord> toNationalityRows(Customer customer) {
    return switch (customer.getDetails()) {
      case IndividualDetails individual ->
          individual.nationalities().values().stream()
              .map(country -> nationalityRow(customer, country))
              .toList();
      case CorporateDetails _ -> List.of();
    };
  }

  private static void setIndividualColumns(CustomerRecord row, IndividualDetails details) {
    PersonalName name = details.name();
    row.setGivenName(name.givenName());
    row.setMiddleName(name.middleName());
    row.setFamilyName(name.familyName());
    row.setDateOfBirth(details.dateOfBirth());
    row.setGender(details.gender().name());
    row.setCountryOfResidence(details.countryOfResidence().value());
  }

  private static void setCorporateColumns(CustomerRecord row, CorporateDetails details) {
    row.setRegisteredName(details.registeredName());
    row.setRegistrationNumber(details.registrationNumber());
    row.setCountryOfIncorporation(details.countryOfIncorporation().value());
  }

  private static CustomerNationalityRecord nationalityRow(Customer customer, CountryCode country) {
    CustomerNationalityRecord row = new CustomerNationalityRecord();
    row.setCustomerId(customer.getId().id());
    // Denormalized so the composite FK can prove a nationality never reaches a corporate.
    row.setKind(customer.getType().name());
    row.setCountryCode(country.value());
    return row;
  }

  private OffsetDateTime timestamp(Instant instant) {
    return OffsetDateTime.ofInstant(instant, clock.getZone());
  }
}
