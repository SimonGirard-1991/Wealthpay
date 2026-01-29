package org.girardsimon.wealthpay.account.domain.exception;

public class InvalidAccountEventStreamException extends RuntimeException {
  public InvalidAccountEventStreamException(String message) {
    super(message);
  }
}
