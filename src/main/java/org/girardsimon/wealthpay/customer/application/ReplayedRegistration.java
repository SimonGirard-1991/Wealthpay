package org.girardsimon.wealthpay.customer.application;

import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.CustomerId;

/**
 * An earlier attempt carrying the same payload already committed under this key. The stored
 * customer id is what lets the replay re-read the customer and rebuild the identical response,
 * which is why the response body is not duplicated into the idempotency row.
 */
public record ReplayedRegistration(CustomerId customerId) implements RegistrationOutcome {

  public ReplayedRegistration {
    requireCustomerId(customerId);
  }

  private static void requireCustomerId(CustomerId customerId) {
    if (customerId == null) {
      throw new CustomerRowCorruptException("Replayed registration is missing its customer id");
    }
  }
}
