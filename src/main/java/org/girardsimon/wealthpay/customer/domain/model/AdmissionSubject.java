package org.girardsimon.wealthpay.customer.domain.model;

import java.util.Map;
import java.util.Set;

/** What the admission policy is evaluated against. */
public sealed interface AdmissionSubject permits IndividualSubject, CorporateSubject {

  /**
   * Every country this subject is tied to, indexed by how. One deny-side hit is enough to refuse.
   */
  Map<ConnectingFactor, Set<CountryCode>> connections();

  /** Where this subject is established, which is what licensing keys on. */
  ConnectingFactor establishmentFactor();
}
