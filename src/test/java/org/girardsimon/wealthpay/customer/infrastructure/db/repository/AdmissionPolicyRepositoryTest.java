package org.girardsimon.wealthpay.customer.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.girardsimon.wealthpay.customer.jooq.tables.AdmissionPolicy.ADMISSION_POLICY;
import static org.girardsimon.wealthpay.customer.jooq.tables.LicensedCountry.LICENSED_COUNTRY;

import org.girardsimon.wealthpay.customer.application.CountryAdmissionPolicy;
import org.girardsimon.wealthpay.customer.domain.exception.AdmissionPolicyCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionMatch;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionPolicySnapshot;
import org.girardsimon.wealthpay.customer.domain.model.ConnectingFactor;
import org.girardsimon.wealthpay.customer.domain.model.CountryCode;
import org.girardsimon.wealthpay.customer.domain.model.Restriction;
import org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper.AdmissionPolicyRowToSnapshotMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;

/**
 * Unlike its sibling container tests this one writes inside the {@code @JooqTest} transaction and
 * lets it roll back, because the policy tables are global singletons with no per-test identifier to
 * scope an assertion by. Anything committed here would be visible to every later test.
 */
@JooqTest
@Import({AdmissionPolicyRepository.class, AdmissionPolicyRowToSnapshotMapper.class})
class AdmissionPolicyRepositoryTest extends AbstractCustomerContainerTest {

  @Autowired private CountryAdmissionPolicy countryAdmissionPolicy;

  @Autowired private DSLContext dslContext;

  @Test
  void reads_the_seeded_us_person_rule_as_three_restrictions() {
    // Act
    AdmissionPolicySnapshot snapshot = countryAdmissionPolicy.load();

    // Assert - the entity limb is the one a flat deny-list would have missed, so it is named
    assertThat(snapshot.restrictions())
        .contains(
            usPerson(ConnectingFactor.NATIONALITY),
            usPerson(ConnectingFactor.RESIDENCE),
            usPerson(ConnectingFactor.INCORPORATION));
  }

  @Test
  void reads_the_policy_version_the_rules_were_read_at() {
    // Arrange - read dynamically rather than pinned to 1, so a later seed migration does not turn
    // this into a failure that says nothing
    long stored =
        dslContext
            .select(ADMISSION_POLICY.VERSION)
            .from(ADMISSION_POLICY)
            .fetchOne(ADMISSION_POLICY.VERSION);

    // Act
    AdmissionPolicySnapshot snapshot = countryAdmissionPolicy.load();

    // Assert
    assertThat(snapshot.policyVersion()).isEqualTo(stored);
  }

  @Test
  void groups_licensed_countries_by_their_connecting_factor() {
    // Arrange
    insertLicence("CH", ConnectingFactor.RESIDENCE);
    insertLicence("FR", ConnectingFactor.RESIDENCE);
    insertLicence("CH", ConnectingFactor.INCORPORATION);

    // Act
    AdmissionPolicySnapshot snapshot = countryAdmissionPolicy.load();

    // Assert
    assertThat(snapshot.licensedFor(ConnectingFactor.RESIDENCE))
        .containsExactlyInAnyOrder(CountryCode.of("CH"), CountryCode.of("FR"));
    assertThat(snapshot.licensedFor(ConnectingFactor.INCORPORATION))
        .containsExactly(CountryCode.of("CH"));
  }

  /**
   * An empty table must arrive as an empty result, never as null. That property comes entirely from
   * the {@code coalesce(jsonb_agg(...), jsonb_build_array())} in jOOQ's MULTISET emulation; without
   * it the mapper receives null, throws {@code NullPointerException}, and its passthrough of that
   * turns an unseeded allow-list into a 500 with no corruption alert. {@code licensed_country}
   * ships empty by design, so this is the database's current state rather than a constructed one.
   */
  @Test
  void reads_an_unseeded_allow_list_as_an_empty_result_rather_than_null() {
    // Act
    AdmissionPolicySnapshot snapshot = countryAdmissionPolicy.load();

    // Assert
    assertThat(snapshot.licences()).isEmpty();
  }

  /**
   * The fail-closed guard for a vanished singleton row, and the failure the eighth exception type
   * exists for: unasserted, a regression that drops the row surfaces as a bare NPE and the distinct
   * corruption alert never fires. Reachable despite {@code V6}'s deletion guard because {@code
   * DISABLE TRIGGER} is transactional and rolls back with the slice - that guard makes the row
   * undeletable by the application, not untestable.
   */
  @Test
  void reports_a_missing_policy_version_row_as_policy_corruption() {
    // Arrange
    dslContext.execute(
        "ALTER TABLE customer.admission_policy DISABLE TRIGGER trg_admission_policy_no_delete");
    dslContext.deleteFrom(ADMISSION_POLICY).execute();

    // Act + Assert
    assertThatExceptionOfType(AdmissionPolicyCorruptException.class)
        .isThrownBy(() -> countryAdmissionPolicy.load());
  }

  private void insertLicence(String countryCode, ConnectingFactor factor) {
    dslContext
        .insertInto(
            LICENSED_COUNTRY, LICENSED_COUNTRY.COUNTRY_CODE, LICENSED_COUNTRY.CONNECTING_FACTOR)
        .values(countryCode, factor.name())
        .execute();
  }

  private static AdmissionMatch usPerson(ConnectingFactor factor) {
    return new AdmissionMatch(Restriction.RESTRICTED_PERSON, factor, CountryCode.of("US"));
  }
}
