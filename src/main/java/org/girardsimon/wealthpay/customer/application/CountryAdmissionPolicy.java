package org.girardsimon.wealthpay.customer.application;

import org.girardsimon.wealthpay.customer.domain.model.AdmissionPolicySnapshot;

/**
 * Supplies the admission rules as they stand right now - whether we onboard someone connected to a
 * country, which changes weekly, as opposed to whether that country exists, which does not.
 */
public interface CountryAdmissionPolicy {

  /**
   * Reads both rule sets and the version they were read at in a single statement, so the rules and
   * the version cannot come from different snapshots.
   */
  AdmissionPolicySnapshot load();
}
