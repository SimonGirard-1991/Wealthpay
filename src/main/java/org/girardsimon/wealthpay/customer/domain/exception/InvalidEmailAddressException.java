package org.girardsimon.wealthpay.customer.domain.exception;

public class InvalidEmailAddressException extends RuntimeException {

  public InvalidEmailAddressException(String message) {
    super(message);
  }
}
