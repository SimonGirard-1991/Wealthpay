package org.girardsimon.wealthpay.customer.application;

import java.time.Instant;
import java.util.Optional;
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

  /** Aggregate and transition sequence from one statement, so both describe the same snapshot. */
  Optional<LoadedCustomer> load(CustomerId customerId);

  /**
   * Raw state, bypassing {@code Customer.reconstitute}: the caller re-asserts what it relies on.
   */
  Optional<CustomerState> findStateAfterSupersededTransition(CustomerId customerId);

  /**
   * Writes the transition and applies the status change in one statement. The outcome is the
   * authority on who applied it, not the aggregate's in-memory guard, which is true on every racing
   * caller.
   */
  TransitionOutcome recordTransitionAndApply(
      LoadedCustomer loaded, CustomerStatus target, Instant occurredAt, Actor actor);
}
