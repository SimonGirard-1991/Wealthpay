package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.girardsimon.wealthpay.account.jooq.Routines.manageOutboxPartitions;
import static org.girardsimon.wealthpay.account.jooq.Tables.OUTBOX_CLEANUP_LOG;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(OutboxCleanupScheduler.class);

  private final DSLContext dslContext;
  private final Clock clock;
  private final int retentionDays;
  private final Counter successCounter;
  private final Counter failureCounter;
  private final AtomicLong lastRunEpochSeconds;

  public OutboxCleanupScheduler(
      DSLContext dslContext,
      Clock clock,
      MeterRegistry meterRegistry,
      @Value("${outbox.cleanup.retention-days:3}") int retentionDays) {
    this.dslContext = dslContext;
    this.clock = clock;
    this.retentionDays = retentionDays;

    this.successCounter =
        Counter.builder("outbox.cleanup.runs")
            .tag("outcome", "success")
            .description("Number of successful outbox cleanup runs")
            .register(meterRegistry);

    this.failureCounter =
        Counter.builder("outbox.cleanup.runs")
            .tag("outcome", "failure")
            .description("Number of failed outbox cleanup runs")
            .register(meterRegistry);

    this.lastRunEpochSeconds = new AtomicLong(initLastRunFromLog());
    meterRegistry.gauge(
        "outbox.cleanup.last_run.seconds", lastRunEpochSeconds, AtomicLong::doubleValue);
  }

  /**
   * Spring fallback for environments without pg_cron. In multi-instance deployments this fires on
   * every replica; the underlying function is idempotent, so concurrent runs are harmless but
   * wasteful. Consider ShedLock (@SchedulerLock) or a property-gated toggle when scaling out.
   */
  @Scheduled(cron = "0 0 3 * * *")
  public void cleanupPartitions() {
    log.info("Starting outbox partition cleanup with retention={} days", retentionDays);
    try {
      manageOutboxPartitions(dslContext.configuration(), retentionDays);
      lastRunEpochSeconds.set(clock.instant().getEpochSecond());
      successCounter.increment();
      log.info("Outbox partition cleanup completed successfully");
    } catch (DataAccessException e) {
      failureCounter.increment();
      log.error("Outbox partition cleanup failed", e);
    }
  }

  /**
   * Reads the most recent cleanup timestamp from outbox_cleanup_log so the gauge survives restarts.
   * Returns 0 only when no cleanup has ever run (fresh deployment).
   */
  private long initLastRunFromLog() {
    try {
      return Optional.ofNullable(
              dslContext
                  .select(DSL.max(OUTBOX_CLEANUP_LOG.RUN_AT))
                  .from(OUTBOX_CLEANUP_LOG)
                  .fetchOneInto(OffsetDateTime.class))
          .map(lastRun -> lastRun.toInstant().getEpochSecond())
          .orElse(0L);
    } catch (DataAccessException e) {
      log.warn("Could not read last cleanup run from outbox_cleanup_log", e);
      return 0L;
    }
  }
}
