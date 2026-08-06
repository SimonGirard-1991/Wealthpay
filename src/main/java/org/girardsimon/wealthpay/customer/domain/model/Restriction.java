package org.girardsimon.wealthpay.customer.domain.model;

/**
 * Why a subject may be refused admission.
 *
 * <p>Declaration order is the sort order, most serious first, and it is load-bearing: reordering
 * these constants changes which match {@link RefusedDecision#primary()} reports in every historical
 * audit row.
 */
public enum Restriction {
  /** Sanctions or prohibited jurisdictions. Screens against every connecting factor. */
  SANCTIONED,
  /** A restricted person category such as US persons. Follows the subject, not the market. */
  RESTRICTED_PERSON,
  /** We hold no license covering where this subject is established. */
  UNLICENSED
}
