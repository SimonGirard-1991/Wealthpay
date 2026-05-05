package org.girardsimon.wealthpay.account.application.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.girardsimon.wealthpay.account.application.response.ReservationResponse;
import org.girardsimon.wealthpay.account.application.response.ReservationResult;
import org.girardsimon.wealthpay.account.application.response.ReserveFundsResponse;
import org.girardsimon.wealthpay.account.application.response.TransactionStatus;
import org.girardsimon.wealthpay.account.domain.exception.InsufficientFundsException;
import org.girardsimon.wealthpay.account.domain.exception.InvalidAccountEventStreamException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationNotFoundException;
import org.girardsimon.wealthpay.account.domain.exception.ReservationStoreInconsistencyException;
import org.girardsimon.wealthpay.account.domain.exception.TransactionIdConflictException;
import org.girardsimon.wealthpay.account.domain.model.AccountId;
import org.girardsimon.wealthpay.account.domain.model.AccountIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.ReservationId;
import org.girardsimon.wealthpay.account.domain.model.ReservationIdGenerator;
import org.girardsimon.wealthpay.account.domain.model.TransactionId;
import org.girardsimon.wealthpay.account.testsupport.TestAccountIdGenerator;
import org.girardsimon.wealthpay.account.testsupport.TestReservationIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.dao.OptimisticLockingFailureException;

class CommandMetricAspectTest {

  private static final String METRIC = "wealthpay.account.command";

  private static final AccountIdGenerator ACCOUNT_ID_GENERATOR = new TestAccountIdGenerator();
  private static final ReservationIdGenerator RESERVATION_ID_GENERATOR =
      new TestReservationIdGenerator();

  private MeterRegistry meterRegistry;
  private CommandMetricAspect aspect;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    aspect = new CommandMetricAspect(meterRegistry);
  }

  // -- TransactionStatus return type ----------------------------------------

  @Test
  void records_committed_outcome_when_method_returns_TransactionStatus_COMMITTED() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);

    // Act
    TransactionStatus status = target.creditCommand(TransactionStatus.COMMITTED);

    // Assert
    assertThat(status).isEqualTo(TransactionStatus.COMMITTED);
    assertThat(timerCount("credit", "committed")).isEqualTo(1L);
    assertThat(timerCount("credit", "idempotent")).isZero();
  }

  @Test
  void records_idempotent_outcome_when_method_returns_TransactionStatus_NO_EFFECT() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);

    // Act
    target.creditCommand(TransactionStatus.NO_EFFECT);

    // Assert
    assertThat(timerCount("credit", "idempotent")).isEqualTo(1L);
    assertThat(timerCount("credit", "committed")).isZero();
  }

  // -- ReservationResponse return type --------------------------------------

  @Test
  void records_committed_outcome_when_ReservationResponse_carries_non_NO_EFFECT_result() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);

    // Act
    target.cancelCommand(ReservationResult.CANCELED);

    // Assert
    assertThat(timerCount("cancel", "committed")).isEqualTo(1L);
    assertThat(timerCount("cancel", "idempotent")).isZero();
  }

  @Test
  void records_idempotent_outcome_when_ReservationResponse_carries_NO_EFFECT_result() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);

    // Act
    target.cancelCommand(ReservationResult.NO_EFFECT);

    // Assert
    assertThat(timerCount("cancel", "idempotent")).isEqualTo(1L);
    assertThat(timerCount("cancel", "committed")).isZero();
  }

  // -- ReserveFundsResponse return type -------------------------------------

  @Test
  void records_committed_outcome_when_ReserveFundsResponse_carries_RESERVED_result() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);

    // Act
    target.reserveCommand(ReservationResult.RESERVED);

    // Assert
    assertThat(timerCount("reserve", "committed")).isEqualTo(1L);
    assertThat(timerCount("reserve", "idempotent")).isZero();
  }

  @Test
  void records_idempotent_outcome_when_ReserveFundsResponse_carries_NO_EFFECT_result() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);

    // Act
    target.reserveCommand(ReservationResult.NO_EFFECT);

    // Assert
    assertThat(timerCount("reserve", "idempotent")).isEqualTo(1L);
    assertThat(timerCount("reserve", "committed")).isZero();
  }

  // -- null return ----------------------------------------------------------

  @Test
  void records_committed_outcome_when_method_returns_null() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);

    // Act
    Object result = target.commandReturningNull();

    // Assert
    assertThat(result).isNull();
    assertThat(timerCount("nullable", "committed")).isEqualTo(1L);
    assertThat(timerCount("nullable", "error")).isZero();
  }

  // -- Exception classification --------------------------------------------

  @Test
  void records_concurrency_conflict_outcome_when_OptimisticLockingFailureException_is_thrown() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);
    OptimisticLockingFailureException toThrow =
        new OptimisticLockingFailureException("version mismatch");

    // Act + Assert
    assertThatExceptionOfType(OptimisticLockingFailureException.class)
        .isThrownBy(() -> target.commandThrowing(toThrow));
    assertThat(timerCount("probe", "concurrency_conflict")).isEqualTo(1L);
    assertThat(timerCount("probe", "error")).isZero();
  }

  @Test
  void records_concurrency_conflict_outcome_when_TransactionIdConflictException_is_thrown() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);
    TransactionIdConflictException conflict =
        new TransactionIdConflictException(
            ACCOUNT_ID_GENERATOR.newId(), TransactionId.of(UUID.randomUUID()));

    // Act + Assert
    assertThatExceptionOfType(TransactionIdConflictException.class)
        .isThrownBy(() -> target.commandThrowing(conflict));
    assertThat(timerCount("probe", "concurrency_conflict")).isEqualTo(1L);
    assertThat(timerCount("probe", "invariant_violation")).isZero();
    assertThat(timerCount("probe", "error")).isZero();
  }

  @Test
  void records_not_found_outcome_when_NotFoundException_from_domain_package_is_thrown() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);
    ReservationNotFoundException toThrow = new ReservationNotFoundException("missing");

    // Act + Assert
    assertThatExceptionOfType(ReservationNotFoundException.class)
        .isThrownBy(() -> target.commandThrowing(toThrow));
    assertThat(timerCount("probe", "not_found")).isEqualTo(1L);
    assertThat(timerCount("probe", "invariant_violation")).isZero();
  }

  @Test
  void records_invariant_violation_outcome_when_customer_driven_domain_exception_is_thrown() {
    // Arrange — InsufficientFunds is a customer-driven 4xx-class outcome
    TestTarget target = proxy(new TestTarget(), aspect);
    InsufficientFundsException toThrow = new InsufficientFundsException();

    // Act + Assert
    assertThatExceptionOfType(InsufficientFundsException.class)
        .isThrownBy(() -> target.commandThrowing(toThrow));
    assertThat(timerCount("probe", "invariant_violation")).isEqualTo(1L);
    assertThat(timerCount("probe", "not_found")).isZero();
    assertThat(timerCount("probe", "error")).isZero();
  }

  @Test
  void records_error_outcome_when_ReservationStoreInconsistencyException_is_thrown() {
    // Arrange — page-worthy data-integrity breach. Should NOT bucket as invariant_violation,
    // because the HTTP layer returns 500 and oncall paging keys off the error metric bucket.
    TestTarget target = proxy(new TestTarget(), aspect);
    ReservationStoreInconsistencyException toThrow =
        new ReservationStoreInconsistencyException("phase store / aggregate diverged");

    // Act + Assert
    assertThatExceptionOfType(ReservationStoreInconsistencyException.class)
        .isThrownBy(() -> target.commandThrowing(toThrow));
    assertThat(timerCount("probe", "error")).isEqualTo(1L);
    assertThat(timerCount("probe", "invariant_violation")).isZero();
  }

  @Test
  void records_error_outcome_when_InvalidAccountEventStreamException_is_thrown() {
    // Arrange — event-stream corruption is page-worthy and HTTP-500 by AccountExceptionHandler.
    TestTarget target = proxy(new TestTarget(), aspect);
    InvalidAccountEventStreamException toThrow =
        new InvalidAccountEventStreamException("corrupted stream");

    // Act + Assert
    assertThatExceptionOfType(InvalidAccountEventStreamException.class)
        .isThrownBy(() -> target.commandThrowing(toThrow));
    assertThat(timerCount("probe", "error")).isEqualTo(1L);
    assertThat(timerCount("probe", "invariant_violation")).isZero();
  }

  @Test
  void records_error_outcome_when_unexpected_exception_outside_domain_package_is_thrown() {
    // Arrange — IllegalStateException is not a domain exception, not OptimisticLocking either
    TestTarget target = proxy(new TestTarget(), aspect);
    IllegalStateException toThrow = new IllegalStateException("boom");

    // Act + Assert
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> target.commandThrowing(toThrow));
    assertThat(timerCount("probe", "error")).isEqualTo(1L);
    assertThat(timerCount("probe", "invariant_violation")).isZero();
  }

  // -- Resilience: meter-registry failure must not break the system ---------

  @Test
  void original_return_value_is_preserved_when_meter_registry_throws_during_recording() {
    // Arrange
    FailingMeterRegistry failingRegistry = new FailingMeterRegistry();
    CommandMetricAspect failingAspect = new CommandMetricAspect(failingRegistry);
    TestTarget target = proxy(new TestTarget(), failingAspect);

    // Act
    TransactionStatus status = target.creditCommand(TransactionStatus.COMMITTED);

    // Assert — wrapped method's return value still propagates cleanly
    assertThat(status).isEqualTo(TransactionStatus.COMMITTED);
    // …AND the failing registry was actually invoked (proves the catch was exercised, not that
    // the test is accidentally passing because the failure path is unreachable).
    assertThat(failingRegistry.timerInvocations.get()).isPositive();
  }

  @Test
  void original_exception_propagates_when_meter_registry_throws_during_recording() {
    // Arrange
    FailingMeterRegistry failingRegistry = new FailingMeterRegistry();
    CommandMetricAspect failingAspect = new CommandMetricAspect(failingRegistry);
    TestTarget target = proxy(new TestTarget(), failingAspect);
    InsufficientFundsException toThrow = new InsufficientFundsException();

    // Act + Assert — original domain exception (not the meter failure) reaches the caller
    assertThatExceptionOfType(InsufficientFundsException.class)
        .isThrownBy(() -> target.commandThrowing(toThrow));
    assertThat(failingRegistry.timerInvocations.get()).isPositive();
  }

  @Test
  void aspect_does_not_throw_when_meter_registry_throws_on_a_committed_path() {
    // Arrange
    FailingMeterRegistry failingRegistry = new FailingMeterRegistry();
    CommandMetricAspect failingAspect = new CommandMetricAspect(failingRegistry);
    TestTarget target = proxy(new TestTarget(), failingAspect);

    // Act + Assert
    assertThatCode(() -> target.creditCommand(TransactionStatus.COMMITTED))
        .doesNotThrowAnyException();
    assertThat(failingRegistry.timerInvocations.get()).isPositive();
  }

  @Test
  void failing_meter_registry_actually_intercepts_timer_creation_through_overridden_path() {
    // Arrange — direct sanity check on the test scaffolding itself: prove the override IS on the
    // call path the aspect uses. If this regresses (e.g. someone changes recordSafely to use a
    // path that bypasses timer(String, Iterable<Tag>)), the resilience tests above stop being
    // meaningful — and this test will fail loudly instead of letting them silently pass.
    FailingMeterRegistry failingRegistry = new FailingMeterRegistry();

    // Act + Assert
    assertThatExceptionOfType(RuntimeException.class)
        .isThrownBy(() -> failingRegistry.timer("any.metric", "k", "v"))
        .withMessageContaining("simulated meter failure");
  }

  // -- Tag preservation ----------------------------------------------------

  @Test
  void preserves_command_tag_value_from_annotation() {
    // Arrange
    TestTarget target = proxy(new TestTarget(), aspect);

    // Act
    target.creditCommand(TransactionStatus.COMMITTED);

    // Assert
    assertThat(meterRegistry.find(METRIC).tag("command", "debit").timer()).isNull();
    assertThat(meterRegistry.find(METRIC).tag("command", "credit").timer()).isNotNull();
  }

  // -- Test scaffolding ----------------------------------------------------

  private static TestTarget proxy(TestTarget target, CommandMetricAspect aspectInstance) {
    AspectJProxyFactory factory = new AspectJProxyFactory(target);
    factory.addAspect(aspectInstance);
    return factory.getProxy();
  }

  private long timerCount(String command, String outcome) {
    Timer timer =
        meterRegistry.find(METRIC).tag("command", command).tag("outcome", outcome).timer();
    return timer == null ? 0L : timer.count();
  }

  /**
   * Test double exposing one annotated method per return-type / throw scenario the aspect needs to
   * classify.
   */
  static class TestTarget {

    @CommandMetric(command = "credit")
    public TransactionStatus creditCommand(TransactionStatus desiredOutcome) {
      return desiredOutcome;
    }

    @CommandMetric(command = "cancel")
    public ReservationResponse cancelCommand(ReservationResult result) {
      AccountId accountId = ACCOUNT_ID_GENERATOR.newId();
      ReservationId reservationId = RESERVATION_ID_GENERATOR.newId();
      return new ReservationResponse(accountId, reservationId, Optional.empty(), result);
    }

    @CommandMetric(command = "reserve")
    public ReserveFundsResponse reserveCommand(ReservationResult result) {
      return new ReserveFundsResponse(RESERVATION_ID_GENERATOR.newId(), result);
    }

    @CommandMetric(command = "nullable")
    public Object commandReturningNull() {
      return null;
    }

    @CommandMetric(command = "probe")
    public Object commandThrowing(RuntimeException toThrow) {
      throw toThrow;
    }
  }

  /**
   * MeterRegistry test double whose timer creation throws. Overrides {@code newTimer(Meter.Id,
   * DistributionStatisticConfig, PauseDetector)} — the protected hook that <em>every</em> public
   * timer factory in {@link MeterRegistry} ultimately reaches (via the package-private {@code
   * timer(Meter.Id, ...)}). Overriding any of the public {@code timer(String, …)} overloads is not
   * sufficient: those overloads are independent paths in Micrometer 1.16+, none of which delegates
   * to the others on the way to the package-private core.
   *
   * <p>Tracks invocation count so resilience tests can assert the override was actually exercised —
   * otherwise a silent regression that bypasses this code path would let the resilience tests pass
   * tautologically.
   */
  static class FailingMeterRegistry extends SimpleMeterRegistry {
    final AtomicInteger timerInvocations = new AtomicInteger();

    @Override
    protected Timer newTimer(
        Meter.Id id,
        DistributionStatisticConfig distributionStatisticConfig,
        PauseDetector pauseDetector) {
      timerInvocations.incrementAndGet();
      throw new RuntimeException("simulated meter failure");
    }
  }
}
