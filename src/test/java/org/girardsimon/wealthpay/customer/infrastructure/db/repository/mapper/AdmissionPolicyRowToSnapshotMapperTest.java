package org.girardsimon.wealthpay.customer.infrastructure.db.repository.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import org.girardsimon.wealthpay.customer.domain.exception.AdmissionPolicyCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionMatch;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionPolicySnapshot;
import org.girardsimon.wealthpay.customer.domain.model.ConnectingFactor;
import org.girardsimon.wealthpay.customer.domain.model.CountryCode;
import org.girardsimon.wealthpay.customer.domain.model.Restriction;
import org.girardsimon.wealthpay.customer.jooq.tables.records.LicensedCountryRecord;
import org.girardsimon.wealthpay.customer.jooq.tables.records.RestrictedCountryRecord;
import org.junit.jupiter.api.Test;

class AdmissionPolicyRowToSnapshotMapperTest {

  private static final long VERSION = 1L;

  private final AdmissionPolicyRowToSnapshotMapper mapper =
      new AdmissionPolicyRowToSnapshotMapper();

  @Test
  void maps_a_restricted_row_to_the_triple_the_evaluation_reads() {
    // Arrange
    RestrictedCountryRecord row =
        new RestrictedCountryRecord("US", "RESTRICTED_PERSON", "NATIONALITY");

    // Act
    AdmissionPolicySnapshot snapshot = mapper.toSnapshot(List.of(row), List.of(), VERSION);

    // Assert
    assertThat(snapshot.restrictions())
        .containsExactly(
            new AdmissionMatch(
                Restriction.RESTRICTED_PERSON, ConnectingFactor.NATIONALITY, CountryCode.of("US")));
  }

  @Test
  void groups_licensed_countries_by_their_connecting_factor() {
    // Arrange
    List<LicensedCountryRecord> rows =
        List.of(
            new LicensedCountryRecord("CH", "RESIDENCE"),
            new LicensedCountryRecord("FR", "RESIDENCE"),
            new LicensedCountryRecord("CH", "INCORPORATION"));

    // Act
    AdmissionPolicySnapshot snapshot = mapper.toSnapshot(List.of(), rows, VERSION);

    // Assert
    assertThat(snapshot.licensedFor(ConnectingFactor.RESIDENCE))
        .containsExactlyInAnyOrder(CountryCode.of("CH"), CountryCode.of("FR"));
    assertThat(snapshot.licensedFor(ConnectingFactor.INCORPORATION))
        .containsExactly(CountryCode.of("CH"));
  }

  @Test
  void reports_no_licence_for_a_factor_the_policy_is_silent_on() {
    // Arrange
    LicensedCountryRecord onlyResidence = new LicensedCountryRecord("CH", "RESIDENCE");

    // Act
    AdmissionPolicySnapshot snapshot =
        mapper.toSnapshot(List.of(), List.of(onlyResidence), VERSION);

    // Assert - the partitioned-outage case: individuals are servable while every corporate is
    // refused, which an aggregate refusal alert cannot distinguish from a healthy policy
    assertThat(snapshot.licensedFor(ConnectingFactor.INCORPORATION)).isEmpty();
  }

  @Test
  void reports_an_unknown_restriction_as_policy_corruption() {
    // Arrange
    RestrictedCountryRecord row = new RestrictedCountryRecord("US", "EMBARGOED", "NATIONALITY");

    // Act + Assert
    assertThatExceptionOfType(AdmissionPolicyCorruptException.class)
        .isThrownBy(() -> mapper.toSnapshot(List.of(row), List.of(), VERSION))
        .withMessageContaining("restriction");
  }

  @Test
  void reports_an_unknown_connecting_factor_as_policy_corruption() {
    // Arrange
    LicensedCountryRecord row = new LicensedCountryRecord("CH", "ESTABLISHMENT");

    // Act + Assert
    assertThatExceptionOfType(AdmissionPolicyCorruptException.class)
        .isThrownBy(() -> mapper.toSnapshot(List.of(), List.of(row), VERSION))
        .withMessageContaining("connecting_factor");
  }

  /**
   * The gap the column {@code CHECK} cannot close: {@code ^[A-Z]{2}$} accepts XX, and only the
   * value object knows there is no such country.
   */
  @Test
  void reports_a_well_shaped_but_unassigned_country_code_as_policy_corruption() {
    // Arrange
    RestrictedCountryRecord row = new RestrictedCountryRecord("XX", "SANCTIONED", "RESIDENCE");

    // Act + Assert
    assertThatExceptionOfType(AdmissionPolicyCorruptException.class)
        .isThrownBy(() -> mapper.toSnapshot(List.of(row), List.of(), VERSION));
  }

  @Test
  void reports_a_non_positive_version_as_policy_corruption() {
    // Act + Assert
    assertThatExceptionOfType(AdmissionPolicyCorruptException.class)
        .isThrownBy(() -> mapper.toSnapshot(List.of(), List.of(), 0L));
  }
}
