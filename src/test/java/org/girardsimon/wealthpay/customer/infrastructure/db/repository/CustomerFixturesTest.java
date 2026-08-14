package org.girardsimon.wealthpay.customer.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.girardsimon.wealthpay.customer.infrastructure.db.repository.CustomerFixtures.mintCustomerNumber;
import static org.girardsimon.wealthpay.customer.infrastructure.db.repository.CustomerFixtures.mintEmail;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.girardsimon.wealthpay.customer.domain.model.CustomerNumber;
import org.girardsimon.wealthpay.customer.domain.model.EmailAddress;
import org.junit.jupiter.api.Test;

/**
 * The minter computes a Luhn check digit that {@link CustomerNumber} independently verifies, and
 * the schema tests insert its output as raw SQL. A disagreement between the two would therefore
 * pass every column {@code CHECK} and surface only as rows the aggregate cannot read back.
 */
class CustomerFixturesTest {

  private static final int SAMPLE = 1_000;

  @Test
  void every_minted_number_satisfies_the_production_value_object() {
    // Act + Assert
    assertThatNoException()
        .isThrownBy(() -> sample(CustomerFixtures::mintCustomerNumber, CustomerNumber::of));
  }

  @Test
  void every_minted_email_satisfies_the_production_value_object() {
    // Act + Assert
    assertThatNoException().isThrownBy(() -> sample(CustomerFixtures::mintEmail, EmailAddress::of));
  }

  /**
   * Two draws, not a large sample: the number body is eight digits, so distinctness over a thousand
   * draws collides about once in two hundred runs and would be a flaky test rather than a control.
   * A minter that stopped varying is the regression worth catching, and two draws catch it.
   */
  @Test
  void a_minted_number_differs_from_the_next() {
    // Act + Assert
    assertThat(mintCustomerNumber()).isNotEqualTo(mintCustomerNumber());
  }

  /**
   * The one property the round-trip above cannot pin: {@link EmailAddress} canonicalises to lower
   * case, so it accepts an address that {@code ck_customer_email_canonical} would reject.
   */
  @Test
  void a_minted_email_is_already_canonically_lower_case() {
    // Act
    String minted = mintEmail();

    // Assert
    assertThat(minted).isEqualTo(minted.toLowerCase(Locale.ROOT));
  }

  @Test
  void a_minted_email_differs_from_the_next() {
    // Act + Assert
    assertThat(mintEmail()).isNotEqualTo(mintEmail());
  }

  private static void sample(Supplier<String> minter, Consumer<String> parser) {
    Stream.generate(minter).limit(SAMPLE).forEach(parser);
  }
}
