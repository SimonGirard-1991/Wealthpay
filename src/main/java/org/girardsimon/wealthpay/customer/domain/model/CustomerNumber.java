package org.girardsimon.wealthpay.customer.domain.model;

import java.util.regex.Pattern;
import org.girardsimon.wealthpay.customer.domain.exception.InvalidCustomerNumberException;

public record CustomerNumber(String value) {
  private static final Pattern TEN_DIGITS = Pattern.compile("\\d{10}");
  private static final int LUHN_MODULUS = 10;
  private static final int LUHN_DIGIT_SUM_ADJUSTMENT = 9;

  public CustomerNumber {
    if (value == null) {
      throw new IllegalArgumentException("Customer number must not be null");
    }
    if (!TEN_DIGITS.matcher(value).matches()) {
      throw new InvalidCustomerNumberException("Customer number must be exactly 10 digits");
    }
    if (!passesLuhn(value)) {
      throw new InvalidCustomerNumberException("Customer number has an invalid Luhn check digit");
    }
  }

  public static CustomerNumber of(String value) {
    return new CustomerNumber(value);
  }

  private static boolean passesLuhn(String value) {
    int sum = 0;
    boolean doubleDigit = false;
    for (int i = value.length() - 1; i >= 0; i--) {
      int digit = value.charAt(i) - '0';
      if (doubleDigit) {
        digit *= 2;
        // a doubled digit 10..18 collapses to its digit-sum by subtracting 9 (16 -> 1+6 = 7)
        if (digit > LUHN_DIGIT_SUM_ADJUSTMENT) {
          digit -= LUHN_DIGIT_SUM_ADJUSTMENT;
        }
      }
      sum += digit;
      doubleDigit = !doubleDigit;
    }
    return sum % LUHN_MODULUS == 0;
  }
}
