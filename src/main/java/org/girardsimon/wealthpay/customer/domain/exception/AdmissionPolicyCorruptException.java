package org.girardsimon.wealthpay.customer.domain.exception;

/**
 * A persisted admission policy row is unusable. Third tier alongside {@link
 * CustomerRowCorruptException}, and never a 422.
 *
 * <p>Requires its own alert route, which does not exist yet.
 *
 * <p>Its own type rather than the customer-row one because the blast radius differs - a corrupt
 * policy row refuses every registration in the system, not one customer's rehydration, so it needs
 * to be routable to a different alert.
 */
public class AdmissionPolicyCorruptException extends RuntimeException {

  public AdmissionPolicyCorruptException(String message) {
    super(message);
  }
}
