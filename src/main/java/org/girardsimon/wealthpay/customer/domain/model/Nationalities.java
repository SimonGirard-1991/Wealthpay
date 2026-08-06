package org.girardsimon.wealthpay.customer.domain.model;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.girardsimon.wealthpay.customer.domain.exception.InvalidNationalitiesException;

/**
 * The set of nationalities a person holds, deliberately unranked: there is no such thing as a
 * primary nationality, and every regime that matters here asks a set question. FATCA's "is this a
 * US person" is {@code contains(US)}, so a {@code primary} component would invite {@code
 * primary.equals(US)}, which silently misses a FR/US dual national.
 *
 * <p>Unordered by design; sort at serialization boundaries instead.
 */
public record Nationalities(Set<CountryCode> values) {

  private static final int MAX_COUNT = 10;

  public Nationalities {
    values = validated(values);
  }

  // Extracted from the compact constructor so the mutation gate can see it: PITest's default
  // FRECORD filter suppresses every mutant inside a record's canonical constructor.
  private static Set<CountryCode> validated(Set<CountryCode> values) {
    if (values == null) {
      throw new IllegalArgumentException("Nationalities must not be null");
    }
    // Set.copyOf throws NPE on a null element, which would escape as a 500 rather than a 400.
    for (CountryCode value : values) {
      if (value == null) {
        throw new IllegalArgumentException("Nationalities must not contain a null country code");
      }
    }
    // Records do not defensively copy, so without this the caller keeps a live handle on the
    // backing set.
    Set<CountryCode> copy = Set.copyOf(values);
    if (copy.isEmpty()) {
      throw new InvalidNationalitiesException("A customer must hold at least one nationality");
    }
    if (copy.size() > MAX_COUNT) {
      throw new InvalidNationalitiesException(
          "A customer must not hold more than " + MAX_COUNT + " nationalities");
    }
    return copy;
  }

  /**
   * LinkedHashSet, not Set.of: nulls must reach the constructor's check rather than throw here. The
   * set also collapses duplicates, so bounds apply to distinct codes.
   */
  public static Nationalities of(CountryCode... values) {
    if (values == null) {
      throw new IllegalArgumentException("Nationalities must not be null");
    }
    return new Nationalities(new LinkedHashSet<>(Arrays.asList(values)));
  }
}
