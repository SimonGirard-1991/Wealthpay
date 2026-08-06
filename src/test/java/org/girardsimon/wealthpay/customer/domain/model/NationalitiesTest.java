package org.girardsimon.wealthpay.customer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.girardsimon.wealthpay.customer.domain.exception.InvalidNationalitiesException;
import org.junit.jupiter.api.Test;

class NationalitiesTest {

  private static final CountryCode FR = CountryCode.of("FR");
  private static final CountryCode US = CountryCode.of("US");

  @Test
  void holds_the_given_country_codes() {
    // Act
    Nationalities nationalities = Nationalities.of(FR, US);

    // Assert
    assertThat(nationalities.values()).containsExactlyInAnyOrder(FR, US);
  }

  @Test
  void copies_defensively_so_the_caller_cannot_mutate_it_afterwards() {
    // Arrange
    Set<CountryCode> caller = new HashSet<>(Set.of(FR));

    // Act
    Nationalities nationalities = new Nationalities(caller);
    caller.add(US);

    // Assert
    assertThat(nationalities.values()).containsExactly(FR);
  }

  @Test
  void exposes_an_unmodifiable_set() {
    // Arrange
    Nationalities nationalities = Nationalities.of(FR);

    // Act ... Assert
    assertThatExceptionOfType(UnsupportedOperationException.class)
        .isThrownBy(() -> nationalities.values().add(US));
  }

  @Test
  void deduplicates_repeated_country_codes() {
    // Act
    Nationalities nationalities = Nationalities.of(FR, FR, US);

    // Assert
    assertThat(nationalities.values()).containsExactlyInAnyOrder(FR, US);
  }

  @Test
  void rejects_a_null_set() {
    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new Nationalities(null));
  }

  @Test
  void rejects_a_null_element() {
    // Arrange - HashSet permits null where Set.of would not, which is exactly the smuggling path
    Set<CountryCode> withNull = new HashSet<>();
    withNull.add(FR);
    withNull.add(null);

    // Act ... Assert
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new Nationalities(withNull));
  }

  @Test
  void rejects_an_empty_set() {
    // Act ... Assert
    assertThatExceptionOfType(InvalidNationalitiesException.class)
        .isThrownBy(() -> new Nationalities(Set.of()));
  }

  @Test
  void accepts_the_maximum_of_ten() {
    // Arrange
    Set<CountryCode> ten = countryCodes(10);

    // Act
    Nationalities nationalities = new Nationalities(ten);

    // Assert
    assertThat(nationalities.values()).hasSize(10);
  }

  @Test
  void rejects_more_than_ten_and_states_the_limit() {
    // Arrange
    Set<CountryCode> eleven = countryCodes(11);

    // Act ... Assert
    assertThatExceptionOfType(InvalidNationalitiesException.class)
        .isThrownBy(() -> new Nationalities(eleven))
        .withMessageContaining("10");
  }

  @Test
  void applies_the_maximum_after_deduplication() {
    // Arrange - 11 entries collapsing to 10 distinct codes is within the limit
    List<CountryCode> withDuplicate = new ArrayList<>(countryCodes(10));
    withDuplicate.add(withDuplicate.getFirst());

    // Act
    Nationalities nationalities = new Nationalities(new HashSet<>(withDuplicate));

    // Assert
    assertThat(nationalities.values()).hasSize(10);
  }

  private static final List<String> DISTINCT_CODES =
      List.of("FR", "US", "DE", "IT", "ES", "BE", "NL", "PT", "IE", "AT", "SE");

  private static Set<CountryCode> countryCodes(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> CountryCode.of(DISTINCT_CODES.get(i)))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Test
  void factory_rejects_a_null_array() {
    // Act ... Assert - consistent with the constructor rather than an NPE from Arrays.asList
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> Nationalities.of((CountryCode[]) null));
  }
}
