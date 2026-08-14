package org.girardsimon.wealthpay.customer.application;

import org.girardsimon.wealthpay.customer.domain.exception.AdmissionPolicyCorruptException;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionPolicySnapshot;

/**
 * Supplies the admission rules as they stand right now - whether we onboard someone connected to a
 * country, which changes weekly, as opposed to whether that country exists, which does not.
 */
public interface CountryAdmissionPolicy {

  /**
   * Reads both rule sets and the version they were read at in a single statement, so the rules and
   * the version cannot come from different snapshots.
   *
   * @throws AdmissionPolicyCorruptException if the policy cannot be read as a usable rule set - it
   *     never degrades to an empty or partial snapshot. The throw is the fail-closed signal, so a
   *     caller that swallows it and substitutes an empty policy opens every market we hold no
   *     licence in.
   */
  AdmissionPolicySnapshot load();
}
