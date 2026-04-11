package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.girardsimon.wealthpay.account.jooq.Tables.OUTBOX_CLEANUP_LOG;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls {@code outbox_cleanup_log} and exposes Micrometer gauges for alerting. This component never
 * executes partition management — that responsibility belongs to pg_cron (production) or {@link
 * OutboxCleanupFallbackScheduler} (dev/CI).
 */
@Component
public class OutboxCleanupObserver {

  private static final Logger log = LoggerFactory.getLogger(OutboxCleanupObserver.class);
  private static final String SUCCESS = "success";

  private final DSLContext dslContext;
  private final AtomicLong lastRunEpochSeconds;
  private final AtomicLong lastStatusValue;

  public OutboxCleanupObserver(DSLContext dslContext, MeterRegistry meterRegistry) {
    this.dslContext = dslContext;

    CleanupSnapshot initial = initFromLog();

    this.lastRunEpochSeconds =
        new AtomicLong(
            initial.lastSuccessfulCompletedAt() != null
                ? initial.lastSuccessfulCompletedAt().toInstant().getEpochSecond()
                : 0L);
    meterRegistry.gauge(
        "outbox.cleanup.last_run.seconds", lastRunEpochSeconds, AtomicLong::doubleValue);

    // Default to 0 (unhealthy) when the table is empty: "never ran" should not look green.
    // The OutboxCleanupFailed alert will fire, prompting investigation on fresh deploys.
    this.lastStatusValue =
        new AtomicLong(
            Optional.ofNullable(initial.latestStatus())
                .filter(SUCCESS::equals)
                .map(_ -> 1L)
                .orElse(0L));
    meterRegistry.gauge("outbox.cleanup.last_status", lastStatusValue, AtomicLong::doubleValue);
  }

  /**
   * Polls the latest row from {@code outbox_cleanup_log} and updates gauges. Runs on a fixed delay
   * (default 5 minutes), so the observer quickly reflects pg_cron execution results.
   */
  @Scheduled(fixedDelayString = "${outbox.cleanup.poll-interval-ms:300000}")
  public void pollCleanupStatus() {
    try {
      CleanupSnapshot snapshot = fetchCleanupSnapshot();

      if (snapshot.lastSuccessfulCompletedAt() != null) {
        lastRunEpochSeconds.set(snapshot.lastSuccessfulCompletedAt().toInstant().getEpochSecond());
      }

      if (snapshot.latestStatus() != null) {
        lastStatusValue.set(SUCCESS.equals(snapshot.latestStatus()) ? 1L : 0L);
      }
    } catch (DataAccessException e) {
      log.warn("Failed to poll outbox_cleanup_log", e);
    }
  }

  private CleanupSnapshot initFromLog() {
    try {
      return fetchCleanupSnapshot();
    } catch (DataAccessException e) {
      log.warn("Could not read initial state from outbox_cleanup_log", e);
      return new CleanupSnapshot(null, null);
    }
  }

  /**
   * Single query with two scalar subqueries: the latest row's status and the latest successful
   * row's completed_at. Replaces two separate round-trips per poll cycle.
   */
  private CleanupSnapshot fetchCleanupSnapshot() {
    Field<String> latestStatus =
        DSL.select(OUTBOX_CLEANUP_LOG.STATUS)
            .from(OUTBOX_CLEANUP_LOG)
            .orderBy(OUTBOX_CLEANUP_LOG.RUN_AT.desc())
            .limit(1)
            .asField("latest_status");

    Field<OffsetDateTime> latestSuccessfulCompletedAt =
        DSL.select(OUTBOX_CLEANUP_LOG.COMPLETED_AT)
            .from(OUTBOX_CLEANUP_LOG)
            .where(OUTBOX_CLEANUP_LOG.STATUS.eq(SUCCESS))
            .orderBy(OUTBOX_CLEANUP_LOG.COMPLETED_AT.desc())
            .limit(1)
            .asField("latest_successful_completed_at");

    var row = dslContext.select(latestStatus, latestSuccessfulCompletedAt).fetchOne();

    assert row != null;

    return new CleanupSnapshot(row.get(latestStatus), row.get(latestSuccessfulCompletedAt));
  }

  private record CleanupSnapshot(String latestStatus, OffsetDateTime lastSuccessfulCompletedAt) {}
}
