package org.girardsimon.wealthpay.account.infrastructure.metric;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link AdapterMetric}-annotated methods with a latency timer tagged by outcome.
 *
 * <p>{@link OptimisticLockingFailureException} gets its own outcome rather than falling into {@code
 * failure}: it is retryable contention, not a hard infrastructure error, and an error-rate alert
 * wants them apart.
 *
 * <p>Annotation lookup resolves the <em>most-specific method on the target class</em> via {@link
 * AopUtils#getMostSpecificMethod} before reading the annotation. Under JDK dynamic proxies (any
 * adapter implementing an interface, when {@code spring.aop.proxy-target-class=false}), {@link
 * MethodSignature#getMethod()} returns the interface method — which carries no annotation — and a
 * naive {@code getAnnotation} would return null.
 *
 * <p>Failures inside the metric-recording path itself are logged but never thrown; an unresolvable
 * annotation is logged and the metric skipped. Observability must not break the system it observes.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdapterMetricAspect {

  private static final Logger log = LoggerFactory.getLogger(AdapterMetricAspect.class);

  private final MeterRegistry meterRegistry;

  public AdapterMetricAspect(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Around("@annotation(AdapterMetric)")
  public Object measure(ProceedingJoinPoint pjp) throws Throwable {
    Method declaredMethod = ((MethodSignature) pjp.getSignature()).getMethod();
    AdapterMetric adapterMetric = resolveAnnotation(declaredMethod, pjp.getTarget());
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "failure";
    try {
      Object result = pjp.proceed();
      outcome = "success";
      return result;
    } catch (Throwable t) {
      outcome = classifyException(t);
      throw t;
    } finally {
      if (adapterMetric != null) {
        recordSafely(sample, adapterMetric.name(), outcome);
      } else {
        log.warn(
            "AdapterMetric annotation could not be resolved on {}; skipping metric record",
            declaredMethod);
      }
    }
  }

  private static AdapterMetric resolveAnnotation(Method declaredMethod, Object target) {
    if (target == null) {
      return AnnotationUtils.findAnnotation(declaredMethod, AdapterMetric.class);
    }
    Method targetMethod =
        AopUtils.getMostSpecificMethod(declaredMethod, AopProxyUtils.ultimateTargetClass(target));
    return AnnotationUtils.findAnnotation(targetMethod, AdapterMetric.class);
  }

  private static String classifyException(Throwable t) {
    if (t instanceof OptimisticLockingFailureException) {
      return "optimistic_conflict";
    }
    return "failure";
  }

  private void recordSafely(Timer.Sample sample, String name, String outcome) {
    try {
      sample.stop(meterRegistry.timer(name, "outcome", outcome));
    } catch (RuntimeException meterFailure) {
      log.warn(
          "Failed to record adapter metric (name={}, outcome={})", name, outcome, meterFailure);
    }
  }
}
