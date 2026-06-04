package org.girardsimon.wealthpay.customer.domain.model;

public class Customer {
  private final CustomerId id;
  private final CustomerNumber number;
  private final EmailAddress email;
  private final CustomerDetails details;
  private CustomerStatus status;

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
}
