package org.girardsimon.wealthpay.customer.domain.model;

import java.time.Instant;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;

/**
 * The persisted shape of a customer, and the sole input to {@link Customer#reconstitute}. A
 * parameter object rather than a long argument list: several components share a type, so a
 * positional swap would compile and yield a plausible aggregate.
 *
 * <p>{@code activatedAt} is null until the customer is activated. Every other component is
 * mandatory, and a missing one means a corrupt row rather than a caller mistake, since the read
 * side is the only producer.
 */
public record CustomerState(
    CustomerId id,
    CustomerNumber number,
    EmailAddress email,
    CustomerDetails details,
    CustomerStatus status,
    Instant registeredAt,
    Instant activatedAt) {

  public CustomerState {
    requireCompleteRow(id, number, email, details, status, registeredAt);
  }

  private static void requireCompleteRow(
      CustomerId id,
      CustomerNumber number,
      EmailAddress email,
      CustomerDetails details,
      CustomerStatus status,
      Instant registeredAt) {
    if (id == null
        || number == null
        || email == null
        || details == null
        || status == null
        || registeredAt == null) {
      throw new CustomerRowCorruptException("Customer row is missing a mandatory column");
    }
  }
}
