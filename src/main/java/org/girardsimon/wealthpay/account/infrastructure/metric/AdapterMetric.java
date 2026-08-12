package org.girardsimon.wealthpay.account.infrastructure.metric;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.girardsimon.wealthpay.account.application.metric.CommandMetric;

/**
 * Marks an adapter (infrastructure) method for latency + outcome instrumentation. The companion
 * aspect emits a Timer named after {@link #name()} tagged with {@code outcome} (classified from the
 * thrown exception, or {@code success} on a clean return).
 *
 * <p>For application-service command timing use {@link CommandMetric} instead: its outcome lattice
 * is domain-flavored and must not be conflated with adapter-level outcomes.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdapterMetric {
  String name();
}
