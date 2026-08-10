package org.girardsimon.wealthpay.customer.application;

/** This attempt took the key and owns the registration; no earlier attempt has committed one. */
public record ClaimedRegistration() implements RegistrationOutcome {}
