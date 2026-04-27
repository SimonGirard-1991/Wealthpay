package org.girardsimon.wealthpay.account.application.metric;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.girardsimon.wealthpay.account.application.response.ReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReservationResult;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsResponse;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.domain.exception.InvalidAccountEventStreamException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationStoreInconsistencyException;
import org.girardsimon.wealthpay.account.domain.exception.TransactionIdConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link CommandMetric}-annotated methods with a latency timer tagged by command and outcome.
 *
 * <p>Ordered at {@link Ordered#HIGHEST_PRECEDENCE} so it sits <em>outside</em> Spring's {@code
 * TransactionInterceptor} (which defaults to {@link Ordered#LOWEST_PRECEDENCE}). This is
 * intentional: the timer must include the transaction commit phase, which can dominate latency
 * under {@code synchronous_commit=on}.
 *
 * <p>Outcome lattice:
 *
 * <ul>
 *   <li>{@code committed} — non-exceptional return (including {@code null}).
 *   <li>{@code idempotent} — {@link TransactionStatus#NO_EFFECT}, or a {@link ReservationResponse}
 *       / {@link ReserveFundsResponse} carrying {@link ReservationResult#NO_EFFECT}.
 *   <li>{@code concurrency_conflict} — {@link OptimisticLockingFailureException} (event-store
 *       version mismatch on append) <em>and</em> {@link TransactionIdConflictException} (same
 *       transaction id reused with a different fingerprint — a client-side correctness/replay
 *       failure that is operationally a conflict, not a domain-rule violation).
 *   <li>{@code not_found} — any domain exception whose simple name ends in {@code
 *       NotFoundException}.
 *   <li>{@code invariant_violation} — any other exception in the {@code account.domain.exception}
 *       package <em>except</em> the data-integrity exceptions listed below. Customer-driven domain
 *       rule rejections (insufficient funds, currency mismatch, account inactive, reservation
 *       already canceled, …). HTTP 4xx-class. Not page-worthy.
 *   <li>{@code error} — internal/infrastructure failure or "should never happen" data-integrity
 *       breach. Includes anything outside the domain package (DB down, broken pool, unexpected
 *       runtime error) <em>plus</em> the explicit page-worthy domain exceptions {@link
 *       ReservationStoreInconsistencyException} and {@link InvalidAccountEventStreamException},
 *       which the HTTP layer also returns as 500. Reserved for genuinely page-worthy failures so
 *       any error-rate alert built on this bucket stays trustworthy and consistent with HTTP 5xx
 *       paging.
 * </ul>
 *
 * <p>The annotation is read explicitly from the {@link MethodSignature} rather than via Spring's
 * {@code @annotation(...)} parameter binding. Both work in a Spring-managed context, but explicit
 * lookup also works under {@code AspectJProxyFactory} (the unit-test scaffold), which has known
 * limitations binding {@code JoinPointMatch} for advice with bound annotation parameters.
 *
 * <p>Failures inside the metric-recording path itself are logged but never thrown — an
 * observability layer must not break the system it observes. If the meter registry misbehaves the
 * original method's return value or thrown exception still propagates correctly to the caller.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommandMetricAspect {

  static final String METRIC_NAME = "wealthpay.account.command";

  private static final Logger log = LoggerFactory.getLogger(CommandMetricAspect.class);

  private static final String DOMAIN_EXCEPTION_PACKAGE =
      "org.girardsimon.wealthpay.account.domain.exception.";

  private final MeterRegistry meterRegistry;

  public CommandMetricAspect(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Around("@annotation(org.girardsimon.wealthpay.account.application.metric.CommandMetric)")
  public Object measure(ProceedingJoinPoint pjp) throws Throwable {
    CommandMetric commandMetric =
        ((MethodSignature) pjp.getSignature()).getMethod().getAnnotation(CommandMetric.class);
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "error";
    try {
      Object result = pjp.proceed();
      outcome = classify(result);
      return result;
    } catch (Throwable t) {
      outcome = classifyException(t);
      throw t;
    } finally {
      recordSafely(sample, commandMetric.command(), outcome);
    }
  }

  /**
   * Records the timer via {@link MeterRegistry#timer(String, String...)}, swallowing any
   * meter-registry failure. Observability must never break the system it observes — if the registry
   * throws, the original method's return value or thrown exception must still propagate correctly
   * out of {@link #measure}.
   *
   * <p>Uses the registry's public {@code timer(String, String...)} factory rather than {@code
   * Timer.builder(...).register(registry)} on purpose: the builder path goes through a
   * package-private code path inside Micrometer, which makes it harder to inject a failing registry
   * under unit tests. The public factory is functionally equivalent here (no builder options are
   * used — histogram percentiles are configured at the registry level).
   */
  private void recordSafely(Timer.Sample sample, String command, String outcome) {
    try {
      sample.stop(meterRegistry.timer(METRIC_NAME, "command", command, "outcome", outcome));
    } catch (RuntimeException meterFailure) {
      log.warn(
          "Failed to record command metric (command={}, outcome={})",
          command,
          outcome,
          meterFailure);
    }
  }

  private static String classify(Object result) {
    if (result instanceof TransactionStatus status) {
      return status == TransactionStatus.NO_EFFECT ? "idempotent" : "committed";
    }
    if (result instanceof ReservationResponse response) {
      return response.reservationResult() == ReservationResult.NO_EFFECT
          ? "idempotent"
          : "committed";
    }
    if (result instanceof ReserveFundsResponse response) {
      return response.reservationResult() == ReservationResult.NO_EFFECT
          ? "idempotent"
          : "committed";
    }
    return "committed";
  }

  private static String classifyException(Throwable t) {
    if (t instanceof OptimisticLockingFailureException
        || t instanceof TransactionIdConflictException) {
      return "concurrency_conflict";
    }
    if (t instanceof ReservationStoreInconsistencyException
        || t instanceof InvalidAccountEventStreamException) {
      // Page-worthy data-integrity breaches. Lifted out of invariant_violation so they appear
      // in any error-rate alert and stay consistent with the HTTP layer's 500 + log.error.
      return "error";
    }
    String className = t.getClass().getName();
    if (className.startsWith(DOMAIN_EXCEPTION_PACKAGE)) {
      return className.endsWith("NotFoundException") ? "not_found" : "invariant_violation";
    }
    return "error";
  }
}
