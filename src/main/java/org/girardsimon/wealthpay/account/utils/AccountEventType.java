package org.girardsimon.wealthpay.account.utils;

import java.util.Locale;
import java.util.regex.Pattern;

public enum AccountEventType {
  ACCOUNT_OPENED,
  FUNDS_CREDITED,
  FUNDS_DEBITED,
  FUNDS_RESERVED,
  RESERVATION_CANCELLED,
  RESERVATION_CAPTURED,
  ACCOUNT_CLOSED;

  private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("(\\p{Ll})(\\p{Lu})");

  public static AccountEventType from(String raw) {
    return AccountEventType.valueOf(
        CAMEL_CASE_BOUNDARY.matcher(raw).replaceAll("$1_$2").toUpperCase(Locale.US));
  }
}
