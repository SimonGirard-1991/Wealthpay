package org.girardsimon.wealthpay.customer.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;
import org.girardsimon.wealthpay.customer.domain.exception.InvalidEmailAddressException;

public record EmailAddress(String value) {
  // Pragmatic shape check, not RFC 5322: one '@', no whitespace, a dotted domain. Deliberately
  // lenient (admits e.g. consecutive/leading dots) - it proves "looks like an address", not
  // deliverability. Real validation is a confirmation email. Stored lowercased.
  private static final Pattern SHAPE = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
  private static final int MAX_LENGTH = 254;

  public EmailAddress {
    if (value == null) {
      throw new IllegalArgumentException("Email address must not be null");
    }
    String canonical = value.toLowerCase(Locale.ROOT);
    if (canonical.length() > MAX_LENGTH || !SHAPE.matcher(canonical).matches()) {
      throw new InvalidEmailAddressException("Email address format is invalid");
    }
    value = canonical;
  }

  public static EmailAddress of(String value) {
    return new EmailAddress(value);
  }
}
