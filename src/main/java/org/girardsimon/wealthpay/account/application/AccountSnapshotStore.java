package org.girardsimon.wealthpay.account.application;

import java.util.Optional;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountSnapshot;

public interface AccountSnapshotStore {
  Optional<AccountSnapshot> load(AccountId accountId);

  void saveSnapshot(AccountSnapshot accountSnapshot);
}
