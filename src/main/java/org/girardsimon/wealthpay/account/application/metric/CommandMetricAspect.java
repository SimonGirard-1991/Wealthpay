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
 * <p>The annotation is read explicitly from the {@link MethodSignature} rather than via Spring's
 * {@code @annotation(...)} parameter binding, which {@code AspectJProxyFactory} - the unit-test
 * scaffold - cannot reliably bind for advice with bound annotation parameters.
 *
 * <p>Failures inside the metric-recording path itself are logged but never thrown: an observability
 * layer must not break the system it observes.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommandMetricAspect {

  static final String METRIC_NAME = "wealthpay.account.command";

  private static final Logger log = LoggerFactory.getLogger(CommandMetricAspect.class);

  private static final String DOMAIN_EXCEPTION_PACKAGE =
      "org.girardsimon.wealthpay.account.domain.exception.";

  private static final String OUTCOME_COMMITTED = "committed";
  private static final String OUTCOME_IDEMPOTENT = "idempotent";
  private static final String OUTCOME_CONCURRENCY_CONFLICT = "concurrency_conflict";
  private static final String OUTCOME_NOT_FOUND = "not_found";
  private static final String OUTCOME_INVARIANT_VIOLATION = "invariant_violation";
  private static final String OUTCOME_ERROR = "error";

  private final MeterRegistry meterRegistry;

  public CommandMetricAspect(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Around("@annotation(CommandMetric)")
  public Object measure(ProceedingJoinPoint pjp) throws Throwable {
    CommandMetric commandMetric =
        ((MethodSignature) pjp.getSignature()).getMethod().getAnnotation(CommandMetric.class);
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = OUTCOME_ERROR;
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
      return status == TransactionStatus.NO_EFFECT ? OUTCOME_IDEMPOTENT : OUTCOME_COMMITTED;
    }
    if (result instanceof ReservationResponse response) {
      return response.reservationResult() == ReservationResult.NO_EFFECT
          ? OUTCOME_IDEMPOTENT
          : OUTCOME_COMMITTED;
    }
    if (result instanceof ReserveFundsResponse response) {
      return response.reservationResult() == ReservationResult.NO_EFFECT
          ? OUTCOME_IDEMPOTENT
          : OUTCOME_COMMITTED;
    }
    return OUTCOME_COMMITTED;
  }

  private static String classifyException(Throwable t) {
    // A reused transaction id is a client-side replay fault, but it is operationally a conflict
    // rather than a domain-rule rejection, so it shares the bucket.
    if (t instanceof OptimisticLockingFailureException
        || t instanceof TransactionIdConflictException) {
      return OUTCOME_CONCURRENCY_CONFLICT;
    }
    if (t instanceof ReservationStoreInconsistencyException
        || t instanceof InvalidAccountEventStreamException) {
      // Page-worthy data-integrity breaches. Lifted out of invariant_violation so they appear
      // in any error-rate alert and stay consistent with the HTTP layer's 500 + log.error.
      return OUTCOME_ERROR;
    }
    String className = t.getClass().getName();
    if (className.startsWith(DOMAIN_EXCEPTION_PACKAGE)) {
      return className.endsWith("NotFoundException")
          ? OUTCOME_NOT_FOUND
          : OUTCOME_INVARIANT_VIOLATION;
    }
    return OUTCOME_ERROR;
  }
}
