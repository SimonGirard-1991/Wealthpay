package org.girardsimon.wealthpay.account.application;

import io.micrometer.observation.annotation.Observed;
import java.util.List;
import org.girardsimon.wealthpay.account.domain.event.AccountEvent;
import org.girardsimon.wealthpay.account.domain.model.Account;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.springframework.stereotype.Service;

@Service
public class AccountLoader {

  private final AccountEventStore accountEventStore;
  private final AccountSnapshotStore accountSnapshotStore;

  public AccountLoader(
      AccountEventStore accountEventStore, AccountSnapshotStore accountSnapshotStore) {
    this.accountEventStore = accountEventStore;
    this.accountSnapshotStore = accountSnapshotStore;
  }

  @Observed(name = "account.load")
  public Account loadAccount(AccountId accountId) {
    return accountSnapshotStore
        .load(accountId)
        .map(
            accountSnapshot -> {
              List<AccountEvent> eventsAfterSnapshot =
                  accountEventStore.loadEventsAfterVersion(accountId, accountSnapshot.version());
              return Account.rehydrateFromSnapshot(accountSnapshot, eventsAfterSnapshot);
            })
        .orElseGet(() -> Account.rehydrate(accountEventStore.loadEvents(accountId)));
  }
}
