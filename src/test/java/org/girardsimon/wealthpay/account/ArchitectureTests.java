package org.girardsimon.wealthpay.account;

import org.girardsimon.wealthpay.WealthpayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTests {

  @Test
  void verify_modularity() {
    ApplicationModules applicationModules = ApplicationModules.of(WealthpayApplication.class);
    applicationModules.verify();
  }
}
