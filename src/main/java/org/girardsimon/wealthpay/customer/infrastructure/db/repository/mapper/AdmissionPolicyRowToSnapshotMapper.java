package org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.girardsimon.wealthpay.customer.domain.exception.AdmissionPolicyCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionMatch;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionPolicySnapshot;
import org.girardsimon.wealthpay.customer.domain.model.ConnectingFactor;
import org.girardsimon.wealthpay.customer.domain.model.CountryCode;
import org.girardsimon.wealthpay.customer.domain.model.Restriction;
import org.girardsimon.wealthpay.customer.jooq.tables.records.LicensedCountryRecord;
import org.girardsimon.wealthpay.customer.jooq.tables.records.RestrictedCountryRecord;
import org.springframework.stereotype.Component;

/**
 * Rejections here raise {@link AdmissionPolicyCorruptException} rather than the customer-row
 * equivalent, and the distinction is operational: one unusable policy row refuses <em>every</em>
 * registration, so it must reach a different alert from a single corrupt customer.
 */
@Component
public class AdmissionPolicyRowToSnapshotMapper {

  public AdmissionPolicySnapshot toSnapshot(
      List<RestrictedCountryRecord> restrictedRows,
      List<LicensedCountryRecord> licensedRows,
      long policyVersion) {
    try {
      return new AdmissionPolicySnapshot(
          restrictions(restrictedRows), licences(licensedRows), policyVersion);
    } catch (AdmissionPolicyCorruptException e) {
      throw e;
    } catch (NullPointerException | ClassCastException e) {
      // Defects in this mapper rather than in the data; reporting one as corruption would page
      // someone about a healthy policy table.
      throw e;
    } catch (RuntimeException e) {
      throw new AdmissionPolicyCorruptException("Admission policy holds a value no rule can use");
    }
  }

  private static Set<AdmissionMatch> restrictions(List<RestrictedCountryRecord> rows) {
    return rows.stream()
        .map(
            row ->
                new AdmissionMatch(
                    knownValue(
                        Restriction.class, row.getRestriction(), "restricted_country.restriction"),
                    knownValue(
                        ConnectingFactor.class,
                        row.getConnectingFactor(),
                        "restricted_country.connecting_factor"),
                    country(row.getCountryCode(), "restricted_country")))
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Names the table and the offending value, unlike the customer-side mappers. Those stay terse
   * because a customer row is PII; these are country codes and enum names, bank-owned reference
   * data with no applicant in them - and one bad row here refuses every registration in the system,
   * so an operator holding only "something is wrong" has two tables to scan by hand.
   */
  private static CountryCode country(String code, String table) {
    try {
      return CountryCode.of(code);
    } catch (RuntimeException e) {
      // ^[A-Z]{2}$ accepts XX; only the value object knows there is no country behind it.
      throw new AdmissionPolicyCorruptException(
          "Admission policy row in " + table + " carries an unusable country_code " + code);
    }
  }

  /**
   * Absent factors are left absent rather than mapped to an empty set: {@code licensedFor} already
   * reads a missing factor as licensed nowhere, and seeding an empty entry would claim the policy
   * says something about a factor it is silent on.
   */
  private static Map<ConnectingFactor, Set<CountryCode>> licences(
      List<LicensedCountryRecord> rows) {
    return rows.stream()
        .collect(
            Collectors.groupingBy(
                row ->
                    knownValue(
                        ConnectingFactor.class,
                        row.getConnectingFactor(),
                        "licensed_country.connecting_factor"),
                Collectors.mapping(
                    row -> country(row.getCountryCode(), "licensed_country"),
                    Collectors.toUnmodifiableSet())));
  }

  private static <E extends Enum<E>> E knownValue(Class<E> type, String value, String column) {
    // Not valueOf: its IllegalArgumentException is rendered as a 400 blaming the caller.
    return Arrays.stream(type.getEnumConstants())
        .filter(constant -> constant.name().equals(value))
        .findFirst()
        .orElseThrow(
            () ->
                new AdmissionPolicyCorruptException(
                    "Admission policy column " + column + " holds an unknown value " + value));
  }
}
