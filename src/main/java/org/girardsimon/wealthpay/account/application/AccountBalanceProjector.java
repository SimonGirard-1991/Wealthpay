package org.girardsimon.wealthpay.account.application;

import java.util.List;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;

public interface AccountBalanceProjector {

  void project(List<AccountEvent> events);
}
