package org.girardsimon.wealthpay.customer.infrastructure.db.repository;

import java.util.UUID;

/**
 * Fixture values minted per call. A customer number, an email or an id written as a literal in a
 * container test is a defect.
 *
 * <p>One Postgres container serves the whole JVM, and the tests that commit outside the
 * {@code @JooqTest} transaction cannot clean up after themselves - the audit tables are
 * append-only. Their rows stay visible to every later test in the run, so a literal has to be kept
 * clear of every other class's literals by hand. That encodes one file's knowledge of another's
 * constants and breaks the moment execution order changes; it was found when renaming a class
 * reshuffled Surefire's default order and turned 13 green tests red. Minting needs no agreement
 * between files.
 */
final class CustomerFixtures {

  private CustomerFixtures() {}

  /**
   * The leading zero is deliberate: it is the shape the generator emits, and the digit a column
   * typed as a number would eat.
   */
  static String mintCustomerNumber() {
    long body = Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L);
    return withLuhnCheckDigit("0%08d".formatted(body));
  }

  static String mintEmail() {
    return "customer-%s@example.test".formatted(UUID.randomUUID());
  }

  private static String withLuhnCheckDigit(String body) {
    int sum = 0;
    // The body's last digit lands in a doubled position once the check digit is appended.
    boolean doubled = true;
    for (int i = body.length() - 1; i >= 0; i--) {
      int digit = body.charAt(i) - '0';
      if (doubled) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      doubled = !doubled;
    }
    return body + (10 - sum % 10) % 10;
  }
}
