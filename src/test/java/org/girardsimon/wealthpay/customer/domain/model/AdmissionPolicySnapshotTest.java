package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.girardsimon.wealthpay.customer.domain.exception.AdmissionPolicyCorruptException;
import org.junit.jupiter.api.Test;

class AdmissionPolicySnapshotTest {

  private static final CountryCode FR = CountryCode.of("FR");
  private static final CountryCode BE = CountryCode.of("BE");
  private static final CountryCode RU = CountryCode.of("RU");

  private static final AdmissionMatch SANCTIONED_RU =
      new AdmissionMatch(Restriction.SANCTIONED, ConnectingFactor.NATIONALITY, RU);

  @Test
  void holds_both_rule_sets_and_the_version_they_were_read_at() {
    // Act
    AdmissionPolicySnapshot snapshot =
        new AdmissionPolicySnapshot(
            Set.of(SANCTIONED_RU), Map.of(ConnectingFactor.RESIDENCE, Set.of(FR)), 7L);

    // Assert
    assertThat(snapshot.restrictions()).containsExactly(SANCTIONED_RU);
    assertThat(snapshot.licensedFor(ConnectingFactor.RESIDENCE)).containsExactly(FR);
    assertThat(snapshot.policyVersion()).isEqualTo(7L);
  }

  @Test
  void reports_no_licences_for_an_absent_factor() {
    // Arrange - licensed for residence only; corporates are covered nowhere
    AdmissionPolicySnapshot snapshot =
        new AdmissionPolicySnapshot(Set.of(), Map.of(ConnectingFactor.RESIDENCE, Set.of(FR)), 7L);

    // Act ... Assert - an allowlist fails closed, so an absent factor means licensed nowhere
    assertThat(snapshot.licensedFor(ConnectingFactor.INCORPORATION)).isEmpty();
  }

  @Test
  void copies_the_restriction_set_defensively() {
    // Arrange
    Set<AdmissionMatch> caller = new HashSet<>(Set.of(SANCTIONED_RU));

    // Act
    AdmissionPolicySnapshot snapshot = new AdmissionPolicySnapshot(caller, Map.of(), 7L);
    caller.clear();

    // Assert
    assertThat(snapshot.restrictions()).containsExactly(SANCTIONED_RU);
  }

  @Test
  void copies_the_nested_licence_sets_defensively() {
    // Arrange - Map.copyOf alone would leave these value sets shared with the caller
    Set<CountryCode> licensedResidences = new HashSet<>(Set.of(FR));
    Map<ConnectingFactor, Set<CountryCode>> caller = new EnumMap<>(ConnectingFactor.class);
    caller.put(ConnectingFactor.RESIDENCE, licensedResidences);

    // Act
    AdmissionPolicySnapshot snapshot = new AdmissionPolicySnapshot(Set.of(), caller, 7L);
    licensedResidences.add(BE);

    // Assert
    assertThat(snapshot.licensedFor(ConnectingFactor.RESIDENCE)).containsExactly(FR);
  }

  @Test
  void rejects_a_null_restriction_set() {
    // Act ... Assert
    assertThatExceptionOfType(AdmissionPolicyCorruptException.class)
        .isThrownBy(() -> new AdmissionPolicySnapshot(null, Map.of(), 7L));
  }

  @Test
  void rejects_a_null_licence_map() {
    // Act ... Assert
    assertThatExceptionOfType(AdmissionPolicyCorruptException.class)
        .isThrownBy(() -> new AdmissionPolicySnapshot(Set.of(), null, 7L));
  }

  @Test
  void rejects_a_null_licensed_country_and_names_the_offending_factor() {
    // Arrange - HashSet permits the null that an immutable set would not
    Set<CountryCode> withNull = new HashSet<>();
    withNull.add(null);
    Map<ConnectingFactor, Set<CountryCode>> corrupt = Map.of(ConnectingFactor.RESIDENCE, withNull);

    // Act ... Assert - a corrupt policy row refuses every registration, so the operator needs to
    // know which factor is broken
    assertThatExceptionOfType(AdmissionPolicyCorruptException.class)
        .isThrownBy(() -> new AdmissionPolicySnapshot(Set.of(), corrupt, 7L))
        .withMessageContaining("RESIDENCE");
  }

  @Test
  void rejects_a_null_restriction_rule() {
    // Arrange
    Set<AdmissionMatch> withNull = new HashSet<>();
    withNull.add(null);

    // Act ... Assert
    assertThatExceptionOfType(AdmissionPolicyCorruptException.class)
        .isThrownBy(() -> new AdmissionPolicySnapshot(withNull, Map.of(), 7L));
  }

  @Test
  void rejects_a_non_positive_policy_version() {
    // Act ... Assert - a decision recorded against version 0 documents nothing
    assertThatExceptionOfType(AdmissionPolicyCorruptException.class)
        .isThrownBy(() -> new AdmissionPolicySnapshot(Set.of(), Map.of(), 0L));
  }

  @Test
  void rejects_unlicensed_seeded_as_a_deny_side_rule() {
    // Arrange - UNLICENSED has allowlist polarity, so it can only come from the licence check
    Set<AdmissionMatch> crossed =
        Set.of(new AdmissionMatch(Restriction.UNLICENSED, ConnectingFactor.RESIDENCE, FR));

    // Act ... Assert - finding one here means the two policy tables have been crossed
    assertThatExceptionOfType(AdmissionPolicyCorruptException.class)
        .isThrownBy(() -> new AdmissionPolicySnapshot(crossed, Map.of(), 7L));
  }
}
