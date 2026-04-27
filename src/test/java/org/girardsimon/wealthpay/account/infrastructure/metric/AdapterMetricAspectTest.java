package org.girardsimon.wealthpay.account.infrastructure.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.dao.OptimisticLockingFailureException;

class AdapterMetricAspectTest {

  private static final String METRIC = "wealthpay.test.adapter.op";

  private MeterRegistry meterRegistry;
  private AdapterMetricAspect aspect;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    aspect = new AdapterMetricAspect(meterRegistry);
  }

  // -- Outcome classification ----------------------------------------------

  @Test
  void records_success_outcome_on_clean_return() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);

    // Act
    target.run(null);

    // Assert
    assertThat(timerCount("success")).isEqualTo(1L);
    assertThat(timerCount("failure")).isZero();
    assertThat(timerCount("optimistic_conflict")).isZero();
  }

  @Test
  void records_optimistic_conflict_outcome_when_OptimisticLockingFailureException_is_thrown() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);

    // Act + Assert
    assertThatExceptionOfType(OptimisticLockingFailureException.class)
        .isThrownBy(() -> target.run(new OptimisticLockingFailureException("version mismatch")));
    assertThat(timerCount("optimistic_conflict")).isEqualTo(1L);
    assertThat(timerCount("failure")).isZero();
    assertThat(timerCount("success")).isZero();
  }

  @Test
  void records_failure_outcome_on_any_other_runtime_exception() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);

    // Act + Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> target.run(new IllegalStateException("boom")));
    assertThat(timerCount("failure")).isEqualTo(1L);
    assertThat(timerCount("success")).isZero();
    assertThat(timerCount("optimistic_conflict")).isZero();
  }

  // -- Resilience ----------------------------------------------------------

  @Test
  void original_return_propagates_when_meter_registry_throws() {
    // Arrange
    FailingMeterRegistry failing = new FailingMeterRegistry();
    TestTarget target = proxy(new TestTarget(), new AdapterMetricAspect(failing));

    // Act + Assert — adapter returns cleanly even though metric recording failed
    assertThatCode(() -> target.run(null)).doesNotThrowAnyException();
    assertThat(failing.newTimerInvocations.get()).isPositive();
  }

  @Test
  void original_exception_propagates_when_meter_registry_throws() {
    // Arrange
    FailingMeterRegistry failing = new FailingMeterRegistry();
    TestTarget target = proxy(new TestTarget(), new AdapterMetricAspect(failing));

    // Act + Assert — original domain exception (not the meter failure) reaches caller
    assertThatExceptionOfType(OptimisticLockingFailureException.class)
        .isThrownBy(() -> target.run(new OptimisticLockingFailureException("v")));
    assertThat(failing.newTimerInvocations.get()).isPositive();
  }

  // -- Proxy-mode robustness ----------------------------------------------
  //
  // Regression coverage for the JDK-proxy NPE that surfaces under
  // `spring.aop.proxy-target-class=false` (interface-based proxies): when the target implements
  // an interface and Spring uses JDK dynamic proxies, MethodSignature#getMethod() resolves to the
  // *interface* method — which carries no annotation. The aspect must resolve back to the impl
  // method on the target class before reading the annotation, otherwise every annotated call
  // NPEs in `finally`. If you're investigating a production NPE under that property, this is
  // the suite that locks the fix in.

  @Test
  void records_metric_under_JDK_dynamic_proxy_when_annotation_is_only_on_impl() {
    // Arrange — JDK proxy: target exposes only its interface; @AdapterMetric is on the impl.
    InterfaceTarget target = jdkProxy(new AnnotatedImpl(), aspect);

    // Act
    target.run(null);

    // Assert
    assertThat(timerCount("success")).isEqualTo(1L);
  }

  @Test
  void records_metric_under_CGLIB_proxy_when_annotation_is_only_on_impl() {
    // Arrange — CGLIB proxy: same impl, class-based proxying.
    InterfaceTarget target = cglibProxy(new AnnotatedImpl(), aspect);

    // Act
    target.run(null);

    // Assert
    assertThat(timerCount("success")).isEqualTo(1L);
  }

  @Test
  void original_exception_propagates_under_JDK_proxy_with_annotation_only_on_impl() {
    // Arrange
    InterfaceTarget target = jdkProxy(new AnnotatedImpl(), aspect);

    // Act + Assert — exception classification still works through the resolved annotation
    assertThatExceptionOfType(OptimisticLockingFailureException.class)
        .isThrownBy(() -> target.run(new OptimisticLockingFailureException("v")));
    assertThat(timerCount("optimistic_conflict")).isEqualTo(1L);
  }

  // -- Test scaffolding ----------------------------------------------------

  private static TestTarget proxy(TestTarget target, AdapterMetricAspect aspectInstance) {
    AspectJProxyFactory factory = new AspectJProxyFactory(target);
    factory.addAspect(aspectInstance);
    return factory.getProxy();
  }

  private static InterfaceTarget jdkProxy(AnnotatedImpl impl, AdapterMetricAspect aspectInstance) {
    AspectJProxyFactory factory = new AspectJProxyFactory(impl);
    factory.setProxyTargetClass(false);
    factory.setInterfaces(InterfaceTarget.class);
    factory.addAspect(aspectInstance);
    return factory.getProxy();
  }

  private static InterfaceTarget cglibProxy(
      AnnotatedImpl impl, AdapterMetricAspect aspectInstance) {
    AspectJProxyFactory factory = new AspectJProxyFactory(impl);
    factory.setProxyTargetClass(true);
    factory.addAspect(aspectInstance);
    return factory.getProxy();
  }

  private long timerCount(String outcome) {
    Timer timer = meterRegistry.find(METRIC).tag("outcome", outcome).timer();
    return timer == null ? 0L : timer.count();
  }

  /**
   * Test double that mimics an annotated adapter method. If {@code toThrow} is non-null, the method
   * throws it; otherwise returns normally.
   */
  static class TestTarget {

    @AdapterMetric(name = METRIC)
    public void run(RuntimeException toThrow) {
      if (toThrow != null) {
        throw toThrow;
      }
    }
  }

  /**
   * Mirrors the real-world adapter shape: the impl class implements an interface, and {@link
   * AdapterMetric} lives <em>only on the impl method</em> (the interface does not carry it). This
   * is the exact configuration that breaks under JDK proxies if the aspect reads the annotation off
   * the interface method via {@code MethodSignature.getMethod()}.
   */
  interface InterfaceTarget {
    void run(RuntimeException toThrow);
  }

  static class AnnotatedImpl implements InterfaceTarget {
    @Override
    @AdapterMetric(name = METRIC)
    public void run(RuntimeException toThrow) {
      if (toThrow != null) {
        throw toThrow;
      }
    }
  }

  /**
   * MeterRegistry test double whose timer creation throws. Overrides the protected {@code newTimer}
   * hook — the only path every public {@code timer(String, …)} factory routes through (verified by
   * inspecting Micrometer's bytecode; public overloads are independent paths).
   */
  static class FailingMeterRegistry extends SimpleMeterRegistry {
    final AtomicInteger newTimerInvocations = new AtomicInteger();

    @Override
    protected Timer newTimer(
        Meter.Id id,
        DistributionStatisticConfig distributionStatisticConfig,
        PauseDetector pauseDetector) {
      newTimerInvocations.incrementAndGet();
      throw new RuntimeException("simulated meter failure");
    }
  }
}
