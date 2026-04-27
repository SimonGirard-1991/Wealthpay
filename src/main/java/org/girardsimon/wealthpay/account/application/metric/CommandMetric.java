package org.girardsimon.wealthpay.account.application.metric;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an application-service command method for latency + outcome instrumentation. The companion
 * aspect emits a {@code wealthpay.account.command} timer tagged with {@code command} (the value
 * declared here) and {@code outcome} (classified from the return value or thrown exception).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandMetric {
  String command();
}
