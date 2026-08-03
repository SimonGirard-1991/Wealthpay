package org.girardsimon.wealthpay.customer.domain.model;

import java.time.Instant;
import java.util.Optional;

public class Customer {
  private final CustomerId id;
  private final CustomerNumber number;
  private final EmailAddress email;
  private final CustomerDetails details;
  private CustomerStatus status;
  private Instant activatedAt;

  private Customer(
      CustomerId id,
      CustomerNumber number,
      EmailAddress email,
      CustomerDetails details,
      CustomerStatus status) {
    this.id = id;
    this.number = number;
    this.email = email;
    this.details = details;
    this.status = status;
  }

  public static Customer register(
      CustomerId id, CustomerNumber number, EmailAddress email, CustomerDetails details) {
    if (id == null || number == null || email == null || details == null) {
      throw new IllegalArgumentException("Customer requires id, number, email and details");
    }
    return new Customer(id, number, email, details, CustomerStatus.ONBOARDING);
  }

  // Idempotent, and returns true only on the real ONBOARDING -> ACTIVE transition: a state-stored
  // aggregate has no event list for the caller to inspect, so this boolean is the only way to fire
  // activation side effects (welcome comms, integration event) exactly once under retry.
  // A switch *expression* is deliberate - unlike a switch statement over an enum, it is
  // exhaustiveness-checked, so adding SUSPENDED or CLOSED breaks the build here rather than
  // silently reactivating a customer that must stay blocked.
  public boolean activate(Instant occurredAt) {
    if (occurredAt == null) {
      throw new IllegalArgumentException("Customer activation requires an occurrence instant");
    }
    return switch (this.status) {
      case ONBOARDING -> {
        this.status = CustomerStatus.ACTIVE;
        this.activatedAt = occurredAt;
        yield true;
      }
      case ACTIVE -> false;
    };
  }

  // Type is derived, never stored: the exhaustive switch over the sealed CustomerDetails makes a
  // type/details mismatch unrepresentable. A new permit forces a case here (no default branch).
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

  // Empty until the customer is activated. Set once, on the first successful activation, so the
  // "when did this customer become ACTIVE" audit question stays answerable after retries.
  public Optional<Instant> getActivatedAt() {
    return Optional.ofNullable(activatedAt);
  }
}
