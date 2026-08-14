package org.girardsimon.wealthpay.customer.application;

import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;

/**
 * A client error: the caller named a customer that was never registered.
 *
 * <p>Deliberately not named after {@link CustomerRowCorruptException}, which the read paths can
 * also raise. A row already read within this transaction that then cannot be found again is
 * corruption, and the two must reach different operational outcomes - a 404 for the caller against
 * a 500 and a corruption alert. Naming them as a pair invites the handler that treats them as one.
 *
 * <p>Requires a dedicated 404 mapping; none exists yet.
 */
public class CustomerNotFoundException extends RuntimeException {

  public CustomerNotFoundException() {
    super("No customer exists for the requested identifier");
  }
}
