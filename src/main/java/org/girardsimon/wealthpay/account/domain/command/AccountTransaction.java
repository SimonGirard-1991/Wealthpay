package org.girardsimon.wealthpay.account.domain.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;

public interface AccountTransaction {

  TransactionId transactionId();

  AccountId accountId();

  default String fingerprint() {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("", e);
    }
  }
}
