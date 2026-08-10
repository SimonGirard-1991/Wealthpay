package org.girardsimon.wealthpay.customer.application;

import java.time.Instant;
import java.util.Optional;
import org.girardsimon.wealthpay.customer.domain.model.Customer;
import org.girardsimon.wealthpay.customer.domain.model.CustomerId;
import org.girardsimon.wealthpay.customer.domain.model.CustomerState;
import org.girardsimon.wealthpay.customer.domain.model.CustomerStatus;

/** Everything the registration and activation use cases persist about a customer. */
public interface CustomerRepository {

  /**
   * Writes the customer, its nationalities and the link anchoring it to the decision that admitted
   * it. The decision id is a parameter rather than a second call: an admitted decision that never
   * acquires its link falls back to the wrong retention anchor.
   */
  void insert(Customer customer, AdmissionDecisionId admissionDecisionId);

  /**
   * Anchors an admitted decision to a customer that already exists, for the replay branch. Linking
   * the same pair twice is a no-op, so a retry does not fail; linking a second, different decision
   * to one customer is a normal insert, which the composite key allows.
   */
  void linkAdmission(CustomerId customerId, AdmissionDecisionId admissionDecisionId);

  /**
   * Reads the aggregate and its transition sequence in one statement.
   *
   * <p>Not separable into two reads: under READ COMMITTED each statement takes its own snapshot, so
   * a second read could return a sequence belonging to a state the caller never saw.
   */
  Optional<LoadedCustomer> load(CustomerId customerId);

  /**
   * Re-reads the persisted state on a fresh snapshot, for the superseded caller only.
   *
   * <p>Raw state: it does not go through {@code Customer.reconstitute}, so that method's invariants
   * are not applied and the caller must re-assert the ones it depends on.
   */
  Optional<CustomerState> findStateAfterSupersededTransition(CustomerId customerId);

  /**
   * Inserts the transition at {@code expectedSequenceNo + 1} and applies the status change in the
   * same statement, so this path cannot move a customer's status without leaving an audit row.
   *
   * <p>Applying the status change means writing every column it implies, not just {@code status}:
   * {@code activated_at} is constrained to be non-null exactly when the status is ACTIVE, and it
   * must be set from {@code occurredAt} rather than from a database clock, or the activation
   * instant drifts from the transition's own and the periodic-review anniversary moves with it.
   *
   * <p>The returned outcome is the authority on who activated the customer - not the aggregate's
   * in-memory guard, which returns true on every racing caller. One updated row is {@code APPLIED}
   * and none is {@code SUPERSEDED}; the predicate is on the primary key, so any other count is a
   * row the sanctioned write path cannot produce - raise {@code CustomerRowCorruptException}, never
   * fold it into {@code APPLIED}.
   *
   * <p>{@code SUPERSEDED} does not distinguish losing a race from passing a sequence number that
   * was never current - the second is a caller defect, and both land on the same counter.
   *
   * <p>It takes the loaded customer rather than an id, a sequence number and a transition, so that
   * all three provably come from one snapshot. Passed separately they can disagree, and a
   * mismatched set is accepted by the database as a plausible audit row. The implementation derives
   * them via {@link LoadedCustomer#transitionTo}, and must not re-read the sequence: deriving it
   * takes a second snapshot, which is what this method exists to avoid.
   */
  TransitionOutcome recordTransitionAndApply(
      LoadedCustomer loaded, CustomerStatus target, Instant occurredAt, Actor actor);
}
