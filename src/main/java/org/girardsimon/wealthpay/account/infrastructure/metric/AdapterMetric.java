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
 * <p>Outcomes:
 *
 * <ul>
 *   <li>{@code success} — non-exceptional return.
 *   <li>{@code optimistic_conflict} — {@link
 *       org.springframework.dao.OptimisticLockingFailureException} (event-store version mismatch or
 *       other Spring-translated optimistic lock).
 *   <li>{@code failure} — any other exception. The original exception is rethrown unchanged.
 * </ul>
 *
 * <p>Use this annotation for adapter-level timing (DB writes, queue publishes, HTTP calls) where
 * the metric measures an infrastructure operation. For application-service command timing, see
 * {@link CommandMetric} — its outcome lattice is domain-flavored
 * (committed/idempotent/concurrency_conflict/…) and should not be conflated with adapter-level
 * outcomes.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdapterMetric {
  String name();
}
