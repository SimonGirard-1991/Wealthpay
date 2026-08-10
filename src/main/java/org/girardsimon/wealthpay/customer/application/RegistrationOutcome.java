package org.girardsimon.wealthpay.customer.application;

/** What claiming an idempotency key told us about the registration attempt holding it. */
public sealed interface RegistrationOutcome
    permits ClaimedRegistration, InFlightRegistration, ReplayedRegistration {}
