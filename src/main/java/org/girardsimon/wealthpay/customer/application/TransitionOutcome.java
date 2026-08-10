package org.girardsimon.wealthpay.customer.application;

/** Which caller a conditional status transition belonged to. */
public enum TransitionOutcome {
  /** This caller took the sequence number and applied the status change. */
  APPLIED,
  /** Another caller had already taken the sequence number; nothing was written. */
  SUPERSEDED
}
