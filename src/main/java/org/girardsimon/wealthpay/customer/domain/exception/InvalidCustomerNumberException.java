package org.girardsimon.wealthpay.customer.domain.exception;

public class InvalidCustomerNumberException extends RuntimeException {

  public InvalidCustomerNumberException(String message) {
    super(message);
  }
}
