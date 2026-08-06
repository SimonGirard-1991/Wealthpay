package org.girardsimon.wealthpay.customer.domain.model;

import java.time.Instant;
import java.util.Optional;
import org.girardsimon.wealthpay.customer.domain.exception.CustomerRowCorruptException;

public class Customer {
  private final CustomerId id;
  private final CustomerNumber number;
  private final EmailAddress email;
  private final CustomerDetails details;
  private final Instant registeredAt;
  private CustomerStatus status;
  private Instant activatedAt;

  private Customer(
      CustomerId id,
      CustomerNumber number,
      EmailAddress email,
      CustomerDetails details,
      CustomerStatus status,
      Instant registeredAt,
      Instant activatedAt) {
    this.id = id;
    this.number = number;
    this.email = email;
    this.details = details;
    this.status = status;
    this.registeredAt = registeredAt;
    this.activatedAt = activatedAt;
  }

  /** Status is forced to ONBOARDING, never accepted from the caller. */
  public static Customer register(
      CustomerId id,
      CustomerNumber number,
      EmailAddress email,
      CustomerDetails details,
      Instant registeredAt) {
    if (id == null || number == null || email == null || details == null || registeredAt == null) {
      throw new IllegalArgumentException(
          "Customer requires id, number, email, details and a registration instant");
    }
    return new Customer(id, number, email, details, CustomerStatus.ONBOARDING, registeredAt, null);
  }

  /**
   * Loads a customer that already exists, accepting whatever status the row carries. Not a plain
   * all-args constructor: it re-asserts the one invariant a row could violate.
   *
   * <p>Intended for the persistence adapter only. Nothing enforces that yet - an architecture rule
   * restricting callers lands with the adapter itself.
   */
  public static Customer reconstitute(CustomerState state) {
    if (state == null) {
      throw new IllegalArgumentException("Customer state must not be null");
    }
    boolean active = state.status() == CustomerStatus.ACTIVE;
    if (active == (state.activatedAt() == null)) {
      // Corruption rather than a client error: no code path here could have written this row. The
      // message carries no row contents, since it reaches logs and the row is PII.
      throw new CustomerRowCorruptException(
          "Customer row violates the ACTIVE / activation-instant invariant");
    }
    if (state.activatedAt() != null && state.activatedAt().isBefore(state.registeredAt())) {
      throw new CustomerRowCorruptException("Customer row is activated before it was registered");
    }
    return new Customer(
        state.id(),
        state.number(),
        state.email(),
        state.details(),
        state.status(),
        state.registeredAt(),
        state.activatedAt());
  }

  /**
   * Whether to attempt the write, as seen in this transaction's snapshot - not the authority on
   * what happened. Under concurrency two callers both read ONBOARDING, and both get true; only the
   * conditional write's rowcount may gate side effects.
   *
   * <p>A switch expression, not a statement: only the expression form is exhaustiveness-checked, so
   * adding SUSPENDED breaks the build here rather than silently reactivating a blocked customer.
   */
  public boolean activate(Instant occurredAt) {
    if (occurredAt == null) {
      throw new IllegalArgumentException("Customer activation requires an occurrence instant");
    }
    return switch (this.status) {
      case ONBOARDING -> {
        // Inside the arm that assigns, so an idempotent retry on an already-ACTIVE customer still
        // returns false rather than throwing on a skewed clock reading. Not a client error either:
        // occurredAt comes from the use case's Clock, never from a request field, so a backward
        // interval is a system fault - and it would corrupt the AML retention clock.
        if (occurredAt.isBefore(registeredAt)) {
          throw new CustomerRowCorruptException("Customer activation cannot precede registration");
        }
        this.status = CustomerStatus.ACTIVE;
        this.activatedAt = occurredAt;
        yield true;
      }
      case ACTIVE -> false;
    };
  }

  /** Derived, never stored: the exhaustive switch makes a type/details mismatch unrepresentable. */
  public CustomerType getType() {
    return switch (details) {
      case IndividualDetails _ -> CustomerType.INDIVIDUAL;
      case CorporateDetails _ -> CustomerType.CORPORATE;
    };
  }

  public CustomerId getId() {
    return id;
  }

  public CustomerNumber getNumber() {
    return number;
  }

  public EmailAddress getEmail() {
    return email;
  }

  public CustomerDetails getDetails() {
    return details;
  }

  public CustomerStatus getStatus() {
    return status;
  }

  public Instant getRegisteredAt() {
    return registeredAt;
  }

  public Optional<Instant> getActivatedAt() {
    return Optional.ofNullable(activatedAt);
  }
}
