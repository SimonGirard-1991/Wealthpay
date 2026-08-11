package org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.CorporateDetails;
import org.girardsimon.wealthpay.customer.domain.model.CountryCode;
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
import org.girardsimon.wealthpay.customer.jooq.tables.records.CustomerRecord;
import org.springframework.stereotype.Component;

/**
 * Every value-object rejection is reported as a corrupt row rather than left to the type that would
 * otherwise catch it: those classify their failures for a caller who supplied the value, which on a
 * read path means a 4xx and no corruption alert. The schema's column checks are not enough on their
 * own: a Luhn digit, an ISO country and an email shape all pass them and are rejected here.
 * Messages name columns and carry no cause, because both reach the logs and the error body.
 */
@Component
public class CustomerRowToStateMapper {

  public CustomerState toCustomerState(CustomerRecord row, String[] nationalityCodes) {
    if (row == null) {
      throw new CustomerRowCorruptException("Customer row is absent");
    }
    CustomerType kind = knownValue(CustomerType.class, row.getKind(), "kind");
    CustomerDetails details =
        switch (kind) {
          case INDIVIDUAL -> individualDetails(row, nationalityCodes);
          case CORPORATE -> corporateDetails(row);
        };
    return new CustomerState(
        CustomerId.of(requiredColumn(row.getId(), "id")),
        fromColumn("customer_number", row.getCustomerNumber(), CustomerNumber::of),
        fromColumn("email", row.getEmail(), EmailAddress::of),
        details,
        knownValue(CustomerStatus.class, row.getStatus(), "status"),
        instant(requiredColumn(row.getRegisteredAt(), "registered_at")),
        row.getActivatedAt() == null ? null : instant(row.getActivatedAt()));
  }

  private static CustomerDetails individualDetails(CustomerRecord row, String[] nationalityCodes) {
    return constructed(
        "individual details",
        () ->
            new IndividualDetails(
                personalName(row),
                requiredColumn(row.getDateOfBirth(), "date_of_birth"),
                knownValue(Gender.class, row.getGender(), "gender"),
                nationalities(nationalityCodes),
                fromColumn("country_of_residence", row.getCountryOfResidence(), CountryCode::of)));
  }

  private static CustomerDetails corporateDetails(CustomerRecord row) {
    return constructed(
        "corporate details",
        () ->
            new CorporateDetails(
                requiredColumn(row.getRegisteredName(), "registered_name"),
                requiredColumn(row.getRegistrationNumber(), "registration_number"),
                fromColumn(
                    "country_of_incorporation", row.getCountryOfIncorporation(), CountryCode::of)));
  }

  private static PersonalName personalName(CustomerRecord row) {
    return constructed(
        "name",
        () ->
            PersonalName.of(
                requiredColumn(row.getGivenName(), "given_name"),
                row.getMiddleName(),
                requiredColumn(row.getFamilyName(), "family_name")));
  }

  private static Nationalities nationalities(String[] nationalityCodes) {
    if (nationalityCodes == null || nationalityCodes.length == 0) {
      throw new CustomerRowCorruptException("Individual customer row carries no nationality");
    }
    Set<CountryCode> values = new LinkedHashSet<>();
    for (String code : nationalityCodes) {
      values.add(fromColumn("country_code", code, CountryCode::of));
    }
    return constructed("nationalities", () -> new Nationalities(values));
  }

  private static Instant instant(OffsetDateTime value) {
    return value.toInstant();
  }

  private static <T> T fromColumn(String column, String stored, Function<String, T> construct) {
    return constructed(column, () -> construct.apply(requiredColumn(stored, column)));
  }

  private static <T> T constructed(String column, Supplier<T> construct) {
    try {
      return construct.get();
    } catch (CustomerRowCorruptException e) {
      throw e;
    } catch (NullPointerException | ClassCastException e) {
      // A defect in this mapper, not in the row. Reporting it as corruption would page someone
      // about clean data.
      throw e;
    } catch (RuntimeException e) {
      // Not chained: a domain message may echo the value it rejected.
      throw new CustomerRowCorruptException("Customer row carries an invalid " + column);
    }
  }

  private static <T> T requiredColumn(T value, String column) {
    if (value == null) {
      throw new CustomerRowCorruptException("Customer row is missing " + column);
    }
    return value;
  }

  private static <E extends Enum<E>> E knownValue(Class<E> type, String value, String column) {
    String stored = requiredColumn(value, column);
    // Not valueOf: its IllegalArgumentException is rendered as a 400 blaming the caller.
    return Arrays.stream(type.getEnumConstants())
        .filter(constant -> constant.name().equals(stored))
        .findFirst()
        .orElseThrow(
            () -> new CustomerRowCorruptException("Customer row carries an unknown " + column));
  }
}
