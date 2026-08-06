package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AdmissionSubjectTest {

  private static final CountryCode FR = CountryCode.of("FR");
  private static final CountryCode US = CountryCode.of("US");
  private static final CountryCode BE = CountryCode.of("BE");

  @Test
  void individual_connects_by_every_nationality_and_by_residence() {
    // Arrange - a dual FR/US national living in Belgium
    IndividualSubject subject = new IndividualSubject(Nationalities.of(FR, US), BE);

    // Act
    var connections = subject.connections();

    // Assert
    assertThat(connections.get(ConnectingFactor.NATIONALITY)).containsExactlyInAnyOrder(FR, US);
    assertThat(connections.get(ConnectingFactor.RESIDENCE)).containsExactly(BE);
    assertThat(connections).doesNotContainKey(ConnectingFactor.INCORPORATION);
  }

  @Test
  void individual_is_established_where_they_reside() {
    // Arrange
    IndividualSubject subject = new IndividualSubject(Nationalities.of(FR), BE);

    // Act ... Assert - licensing keys on residence, not on nationality
    assertThat(subject.establishmentFactor()).isEqualTo(ConnectingFactor.RESIDENCE);
  }

  @Test
  void corporate_connects_only_by_country_of_incorporation() {
    // Arrange
    CorporateSubject subject = new CorporateSubject(FR);

    // Act
    var connections = subject.connections();

    // Assert - a legal entity has neither a nationality nor a residence
    assertThat(connections).containsOnlyKeys(ConnectingFactor.INCORPORATION);
    assertThat(connections.get(ConnectingFactor.INCORPORATION)).containsExactly(FR);
  }

  @Test
  void corporate_is_established_where_it_is_incorporated() {
    // Arrange
    CorporateSubject subject = new CorporateSubject(FR);

    // Act ... Assert
    assertThat(subject.establishmentFactor()).isEqualTo(ConnectingFactor.INCORPORATION);
  }

  @ParameterizedTest
  @MethodSource("everyPermittedSubject")
  void establishment_factor_is_always_present_among_the_connections(AdmissionSubject subject) {
    // Act
    Map<ConnectingFactor, Set<CountryCode>> connections = subject.connections();

    // Assert - licensing reads connections().get(establishmentFactor()). If a subject type ever
    // reports a factor it does not connect by, the membership check runs over an empty set and is
    // vacuously satisfied, so the subject is ADMITTED - an allowlist failing open.
    assertThat(connections).containsKey(subject.establishmentFactor());
    assertThat(connections.get(subject.establishmentFactor())).isNotEmpty();
  }

  private static Stream<AdmissionSubject> everyPermittedSubject() {
    return Stream.of(new IndividualSubject(Nationalities.of(FR), BE), new CorporateSubject(US));
  }

  @Test
  void individual_connections_are_unmodifiable() {
    // Arrange
    IndividualSubject subject = new IndividualSubject(Nationalities.of(FR), BE);
    Set<CountryCode> nationalities = subject.connections().get(ConnectingFactor.NATIONALITY);

    // Act ... Assert
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> nationalities.add(US));
  }

  @Test
  void individual_rejects_null_nationalities() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new IndividualSubject(null, BE));
  }

  @Test
  void individual_rejects_a_null_residence() {
    // Act ... Assert
    Nationalities nationalities = Nationalities.of(FR);
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new IndividualSubject(nationalities, null));
  }

  @Test
  void corporate_rejects_a_null_country_of_incorporation() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new CorporateSubject(null));
  }
}
