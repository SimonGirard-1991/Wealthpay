package org.girardsimon.wealthpay.shared.infrastructure.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FakeController {

  private final FakeService fakeService;

  public FakeController(FakeService fakeService) {
    this.fakeService = fakeService;
  }

  @GetMapping("/fake")
  public void fake() {
    fakeService.fakeMethod();
  }
}
