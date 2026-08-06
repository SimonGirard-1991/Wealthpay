package org.girardsimon.wealthpay.customer.domain.model;

/**
 * Mints customer numbers. Defined here because the domain owns what a customer number is; the
 * implementation is infrastructure, since minting needs a database sequence.
 */
public interface CustomerNumberGenerator {

  CustomerNumber newNumber();
}
