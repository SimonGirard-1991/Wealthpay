package org.girardsimon.wealthpay.account.utils;

import static org.girardsimon.wealthpay.shared.utils.MapperUtils.getRequiredField;

import java.math.BigDecimal;
import org.girardsimon.wealthpay.account.domain.model.Money;
import org.girardsimon.wealthpay.account.domain.model.SupportedCurrency;
import tools.jackson.databind.JsonNode;

public final class MoneyDeserializerUtils {

  private static final String AMOUNT = "amount";
  private static final String CURRENCY = "currency";

  private MoneyDeserializerUtils() {}

  public static Money extractMoney(JsonNode root) {
    SupportedCurrency currency =
        SupportedCurrency.fromValue(getRequiredField(root, CURRENCY).asString());
    BigDecimal amount = getRequiredField(root, AMOUNT).decimalValue();
    return Money.of(amount, currency);
  }
}
