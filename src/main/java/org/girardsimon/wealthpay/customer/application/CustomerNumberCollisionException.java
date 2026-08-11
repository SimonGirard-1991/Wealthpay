package org.girardsimon.wealthpay.customer.application;

/**
 * An identifier we minted ourselves was already taken. Not a conflict to report to the applicant,
 * who supplied nothing that caused it: it needs a server fault and an alert.
 *
 * <p>The transaction is already aborted when this is thrown - the database raised the violation
 * before it was translated - so minting another number and retrying only works after a rollback, at
 * the transaction boundary. Retrying in place fails with "current transaction is aborted".
 */
public class CustomerNumberCollisionException extends RuntimeException {

  public CustomerNumberCollisionException() {
    super("A minted customer identifier collided with one already stored");
  }
}
