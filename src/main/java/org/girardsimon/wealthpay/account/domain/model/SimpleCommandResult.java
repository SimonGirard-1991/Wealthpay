package org.girardsimon.wealthpay.account.domain.model;

import java.util.List;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;

public record SimpleCommandResult(List<AccountEvent> events) implements HandleResult {}
