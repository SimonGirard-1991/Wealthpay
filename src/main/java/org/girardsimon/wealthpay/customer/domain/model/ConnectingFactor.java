package org.girardsimon.wealthpay.customer.domain.model;

/**
 * The attribute of a subject that ties it to a country, and therefore to a rule.
 *
 * <p>Unlike {@link Restriction}, this order carries no severity meaning - it is an arbitrary but
 * frozen tie-break for when several rules fire at once. Reordering it changes historical audit
 * rows.
 */
public enum ConnectingFactor {
  NATIONALITY,
  RESIDENCE,
  INCORPORATION
}
