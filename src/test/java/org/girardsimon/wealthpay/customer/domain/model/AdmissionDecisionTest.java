package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdmissionDecisionTest {

  private static final long POLICY_VERSION = 42L;

  private static final AdmissionMatch SANCTIONED_NATIONALITY =
      new AdmissionMatch(
          Restriction.SANCTIONED, ConnectingFactor.NATIONALITY, CountryCode.of("RU"));
  private static final AdmissionMatch SANCTIONED_RESIDENCE =
      new AdmissionMatch(Restriction.SANCTIONED, ConnectingFactor.RESIDENCE, CountryCode.of("IR"));
  private static final AdmissionMatch RESTRICTED_NATIONALITY =
      new AdmissionMatch(
          Restriction.RESTRICTED_PERSON, ConnectingFactor.NATIONALITY, CountryCode.of("US"));
  private static final AdmissionMatch UNLICENSED_RESIDENCE =
      new AdmissionMatch(Restriction.UNLICENSED, ConnectingFactor.RESIDENCE, CountryCode.of("BR"));

  @Test
  void admitted_carries_the_policy_version() {
    // Act
    AdmissionDecision decision = new AdmittedDecision(POLICY_VERSION);

    // Assert
    assertThat(decision.policyVersion()).isEqualTo(POLICY_VERSION);
  }

  @Test
  void sorts_a_misordered_list_most_serious_first() {
    // Arrange - deliberately worst-last
    List<AdmissionMatch> misordered =
        List.of(UNLICENSED_RESIDENCE, RESTRICTED_NATIONALITY, SANCTIONED_NATIONALITY);

    // Act
    RefusedDecision refused = new RefusedDecision(misordered, POLICY_VERSION);

    // Assert
    assertThat(refused.matches())
        .containsExactly(SANCTIONED_NATIONALITY, RESTRICTED_NATIONALITY, UNLICENSED_RESIDENCE);
  }

  @Test
  void breaks_ties_within_a_restriction_by_connecting_factor() {
    // Arrange - same restriction, so the connecting factor decides
    List<AdmissionMatch> sameRestriction = List.of(SANCTIONED_RESIDENCE, SANCTIONED_NATIONALITY);

    // Act
    RefusedDecision refused = new RefusedDecision(sameRestriction, POLICY_VERSION);

    // Assert - NATIONALITY precedes RESIDENCE by declaration order
    assertThat(refused.matches()).containsExactly(SANCTIONED_NATIONALITY, SANCTIONED_RESIDENCE);
  }

  @Test
  void breaks_remaining_ties_by_country_code() {
    // Arrange - a dual RU/IR national: same restriction AND same connecting factor, so only the
    // country key can order these. Deliberately supplied worst-alphabetically first.
    AdmissionMatch sanctionedRu =
        new AdmissionMatch(
            Restriction.SANCTIONED, ConnectingFactor.NATIONALITY, CountryCode.of("RU"));
    AdmissionMatch sanctionedIr =
        new AdmissionMatch(
            Restriction.SANCTIONED, ConnectingFactor.NATIONALITY, CountryCode.of("IR"));

    // Act
    RefusedDecision refused =
        new RefusedDecision(List.of(sanctionedRu, sanctionedIr), POLICY_VERSION);

    // Assert - without this key the pair would order by set iteration, which varies per JVM launch
    assertThat(refused.matches()).containsExactly(sanctionedIr, sanctionedRu);
  }

  @Test
  void refusal_carries_the_policy_version() {
    // Act
    RefusedDecision refused = new RefusedDecision(List.of(SANCTIONED_NATIONALITY), POLICY_VERSION);

    // Assert
    assertThat(refused.policyVersion()).isEqualTo(POLICY_VERSION);
  }

  @Test
  void rejects_a_non_positive_policy_version() {
    // Act ... Assert - an audit record must name the policy that produced it
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new RefusedDecision(List.of(SANCTIONED_NATIONALITY), 0L));
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new AdmittedDecision(0L));
  }

  @Test
  void primary_is_the_sanctions_match_when_an_unlicensed_match_is_also_present() {
    // Arrange
    RefusedDecision refused =
        new RefusedDecision(List.of(UNLICENSED_RESIDENCE, SANCTIONED_NATIONALITY), POLICY_VERSION);

    // Act
    AdmissionMatch primary = refused.primary();

    // Assert - a sanctions refusal must never be reported as a licensing one
    assertThat(primary).isEqualTo(SANCTIONED_NATIONALITY);
  }

  @Test
  void ordering_is_independent_of_the_input_order() {
    // Arrange
    List<AdmissionMatch> one = List.of(SANCTIONED_NATIONALITY, UNLICENSED_RESIDENCE);
    List<AdmissionMatch> other = List.of(UNLICENSED_RESIDENCE, SANCTIONED_NATIONALITY);

    // Act
    RefusedDecision first = new RefusedDecision(one, POLICY_VERSION);
    RefusedDecision second = new RefusedDecision(other, POLICY_VERSION);

    // Assert - two evaluations of the same subject must produce the same audit record
    assertThat(first).isEqualTo(second);
  }

  @Test
  void copies_defensively_so_the_caller_cannot_mutate_it_afterwards() {
    // Arrange
    List<AdmissionMatch> caller = new ArrayList<>(List.of(SANCTIONED_NATIONALITY));

    // Act
    RefusedDecision refused = new RefusedDecision(caller, POLICY_VERSION);
    caller.add(UNLICENSED_RESIDENCE);

    // Assert
    assertThat(refused.matches()).containsExactly(SANCTIONED_NATIONALITY);
  }

  @Test
  void exposes_an_unmodifiable_list() {
    // Arrange
    RefusedDecision refused = new RefusedDecision(List.of(SANCTIONED_NATIONALITY), POLICY_VERSION);

    // Act ... Assert
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> refused.matches().add(UNLICENSED_RESIDENCE));
  }

  @Test
  void rejects_an_empty_match_list() {
    // Act ... Assert - refusing without a reason is not representable
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new RefusedDecision(List.of(), POLICY_VERSION));
  }

  @Test
  void rejects_a_null_match_list() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new RefusedDecision(null, POLICY_VERSION));
  }

  @Test
  void rejects_a_lone_null_match_that_the_sort_would_never_visit() {
    // Arrange - a one-element sort never invokes the comparator, so this null would reach primary()
    List<AdmissionMatch> loneNull = Arrays.asList((AdmissionMatch) null);

    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new RefusedDecision(loneNull, POLICY_VERSION));
  }

  @Test
  void rejects_a_null_match_among_several() {
    // Arrange
    List<AdmissionMatch> withNull = Arrays.asList(SANCTIONED_NATIONALITY, null);

    // Act ... Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new RefusedDecision(withNull, POLICY_VERSION));
  }

  @Test
  void match_rejects_a_null_component() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> new AdmissionMatch(null, ConnectingFactor.NATIONALITY, CountryCode.of("RU")));
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new AdmissionMatch(Restriction.SANCTIONED, null, CountryCode.of("RU")));
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> new AdmissionMatch(Restriction.SANCTIONED, ConnectingFactor.NATIONALITY, null));
  }

  @Test
  void restriction_declaration_order_is_most_serious_first() {
    // Act ... Assert - pinned: reordering silently changes every historical primary match
    assertThat(Restriction.values())
        .containsExactly(
            Restriction.SANCTIONED, Restriction.RESTRICTED_PERSON, Restriction.UNLICENSED);
  }

  @Test
  void connecting_factor_relative_order_is_frozen() {
    // Act ... Assert - the order is arbitrary but load-bearing as a tie-break, so existing
    // constants
    // must not be reshuffled. Appending a new one is safe, hence subsequence rather than exact.
    assertThat(ConnectingFactor.values())
        .containsSubsequence(
            ConnectingFactor.NATIONALITY,
            ConnectingFactor.RESIDENCE,
            ConnectingFactor.INCORPORATION);
  }
}
