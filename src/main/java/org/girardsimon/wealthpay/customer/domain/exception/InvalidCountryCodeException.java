package org.girardsimon.wealthpay.customer.domain.exception;

public class InvalidCountryCodeException extends RuntimeException {

  public InvalidCountryCodeException(String message) {
    super(message);
  }
}
