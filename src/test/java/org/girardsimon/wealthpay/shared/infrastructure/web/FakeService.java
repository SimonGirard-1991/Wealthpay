package org.girardsimon.wealthpay.shared.infrastructure.web;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class FakeService {

  public String fakeMethod() {
    return "fake";
  }
}
