package org.girardsimon.wealthpay.customer.application;

import java.time.Clock;
import java.time.Instant;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.Customer;
import org.girardsimon.wealthpay.customer.domain.model.CustomerId;
import org.girardsimon.wealthpay.customer.domain.model.CustomerState;
import org.girardsimon.wealthpay.customer.domain.model.CustomerStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerApplicationService {

  private final CustomerStore customerStore;
  private final Clock clock;

  public CustomerApplicationService(CustomerStore customerStore, Clock clock) {
    this.customerStore = customerStore;
    this.clock = clock;
  }

  /**
   * Activates a customer, or reports the activation another caller already performed.
   *
   * <p>Transactional, unlike the registration flow, whose steps must survive each other's rollback.
   * Here there is one conditional write and one re-read that wants a fresh READ COMMITTED snapshot
   * within the same transaction.
   *
   * @throws CustomerNotFoundException if no customer holds this identifier
   * @throws CustomerRowCorruptException if the persisted row contradicts an invariant the aggregate
   *     guarantees on write
   */
  @Transactional
  public ActivationResult activate(CustomerId customerId, Actor actor) {
    LoadedCustomer loaded = customerStore.load(customerId);
    Instant occurredAt = clock.instant();

    if (!loaded.customer().activate(occurredAt)) {
      return ActivationResult.alreadyActive(activationInstantOf(loaded.customer()));
    }
    // Answering from the boolean above would make the conditional write inert: it is true on every
    // racing caller.
    TransitionOutcome outcome =
        customerStore.recordTransitionAndApply(loaded, CustomerStatus.ACTIVE, occurredAt, actor);
    return switch (outcome) {
      case APPLIED -> ActivationResult.activated(occurredAt);
      case SUPERSEDED -> activationRecordedByTheWinner(customerId);
    };
  }

  private ActivationResult activationRecordedByTheWinner(CustomerId customerId) {
    CustomerState fresh = customerStore.findStateAfterSupersededTransition(customerId);
    // A switch expression because the ON CONFLICT clause this mirrors changes meaning silently when
    // a status is added, and this does not.
    return switch (fresh.status()) {
      case ACTIVE -> ActivationResult.alreadyActive(reReadActivationInstant(fresh.activatedAt()));
      case ONBOARDING ->
          throw new CustomerRowCorruptException(
              "Customer status transition was superseded without applying the status change");
    };
  }

  private static Instant activationInstantOf(Customer customer) {
    return customer
        .getActivatedAt()
        .orElseThrow(
            () ->
                new CustomerRowCorruptException(
                    "Loaded active customer carries no activation instant"));
  }

  private static Instant reReadActivationInstant(Instant activatedAt) {
    if (activatedAt == null) {
      throw new CustomerRowCorruptException(
          "Re-read active customer row carries no activation instant");
    }
    return activatedAt;
  }
}
