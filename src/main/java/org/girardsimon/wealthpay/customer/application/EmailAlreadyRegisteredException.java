package org.girardsimon.wealthpay.customer.application;

/** Client-fixable: another address resolves it. Takes no argument, so the address cannot leak. */
public class EmailAlreadyRegisteredException extends RuntimeException {

  public EmailAlreadyRegisteredException() {
    super("Email address is already registered");
  }
}
