package org.girardsimon.wealthpay.customer.application;

import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.Customer;
import org.girardsimon.wealthpay.customer.domain.model.CustomerStatus;

/**
 * A customer together with the sequence number its status transitions have reached, both read in a
 * single statement.
 *
 * <p>The sequence number is an optimistic-concurrency token rather than aggregate state, so it
 * rides here and nowhere else: {@link Customer} has no such field and {@code CustomerState} would
 * have to accept and ignore one.
 *
 * <p>Not a record, because the status must be captured at construction. {@link Customer} is mutable
 * and the use case activates it before the write is issued, so a status read any later describes
 * the outcome being attempted rather than the row that was loaded. Everything here reports the
 * load, except {@link #customer()}, which hands back the live aggregate the use case activates.
 *
 * <p>Equality is by identity, so stub and verify against the instance {@code load} returned, or
 * {@code any()} - a freshly built equal-looking one never matches.
 *
 * <p>Holding status and sequence from one read is what makes two rows the sanctioned write path
 * cannot produce detectable: an active customer with no transition, and an onboarding customer with
 * one. ONBOARDING is the initial state only - a customer that leaves it never returns, so a later
 * review or restriction is a state of its own rather than a trip back.
 *
 * <p>A backfill importing active customers must synthesize their transition rows, which the audit
 * obligation requires of it regardless.
 */
public final class LoadedCustomer {

  private final Customer customer;
  private final CustomerStatus loadedStatus;
  private final int sequenceNo;

  public LoadedCustomer(Customer customer, int sequenceNo) {
    requireLoadedRow(customer, sequenceNo);
    this.customer = customer;
    this.loadedStatus = customer.getStatus();
    this.sequenceNo = sequenceNo;
  }

  public Customer customer() {
    return customer;
  }

  public int sequenceNo() {
    return sequenceNo;
  }

  /**
   * Pairs the status this customer was loaded with against {@code target}, so a transition's source
   * can neither be supplied by the caller nor drift once the aggregate has been activated.
   *
   * @throws IllegalStateException if the customer was loaded already holding {@code target}, or if
   *     {@code target} is null
   */
  public StatusTransition transitionTo(CustomerStatus target) {
    return new StatusTransition(loadedStatus, target);
  }

  private static void requireLoadedRow(Customer customer, int sequenceNo) {
    if (customer == null) {
      throw new CustomerRowCorruptException("Loaded customer is missing its aggregate");
    }
    if (sequenceNo < 0) {
      throw new CustomerRowCorruptException(
          "Loaded customer carries a negative transition sequence");
    }
    // A switch expression, not an if chain: only the expression form is exhaustiveness-checked, so
    // a new status breaks the build here instead of quietly escaping both guards.
    String defect =
        switch (customer.getStatus()) {
          case ONBOARDING ->
              sequenceNo > 0 ? "Onboarding customer carries a status transition" : null;
          case ACTIVE -> sequenceNo == 0 ? "Active customer carries no status transition" : null;
        };
    if (defect != null) {
      throw new CustomerRowCorruptException(defect);
    }
  }
}
