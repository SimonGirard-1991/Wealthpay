package org.girardsimon.wealthpay.customer.application;

import java.time.Instant;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.Customer;
import org.girardsimon.wealthpay.customer.domain.model.CustomerId;
import org.girardsimon.wealthpay.customer.domain.model.CustomerState;
import org.girardsimon.wealthpay.customer.domain.model.CustomerStatus;

public interface CustomerStore {

  /**
   * Writes the customer, its nationalities and its admission link, or none of the three.
   *
   * @throws EmailAlreadyRegisteredException if the address belongs to another customer
   * @throws CustomerNumberCollisionException if an identifier we minted is already taken - never
   *     report this as the caller's conflict
   */
  void insert(Customer customer, AdmissionDecisionId admissionDecisionId);

  /**
   * Idempotent: a pair already linked writes nothing.
   *
   * @throws CustomerRowCorruptException if the customer is absent, which writing nothing would
   *     otherwise be indistinguishable from
   */
  void linkAdmission(CustomerId customerId, AdmissionDecisionId admissionDecisionId);

  /**
   * Aggregate and transition sequence from one statement, so both describe the same snapshot.
   *
   * @throws CustomerNotFoundException if no customer holds this identifier
   */
  LoadedCustomer load(CustomerId customerId);

  /**
   * Raw state, bypassing {@code Customer.reconstitute}: the caller re-asserts the invariants it
   * relies on, of which the ACTIVE / activation-instant biconditional is the one this read can
   * violate.
   *
   * <p>The conditional write this follows has already established that the row exists, so absence
   * is corruption rather than a miss.
   *
   * @throws CustomerRowCorruptException if the row has disappeared since it was loaded
   */
  CustomerState findStateAfterSupersededTransition(CustomerId customerId);

  /**
   * Writes the transition and applies the status change in one statement. The outcome is the
   * authority on who applied it, not the aggregate's in-memory guard, which is true on every racing
   * caller.
   */
  TransitionOutcome recordTransitionAndApply(
      LoadedCustomer loaded, CustomerStatus target, Instant occurredAt, Actor actor);
}
