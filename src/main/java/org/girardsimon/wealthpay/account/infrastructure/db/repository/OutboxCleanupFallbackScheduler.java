package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.girardsimon.wealthpay.account.jooq.Routines.manageOutboxPartitions;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spring-based fallback for environments without pg_cron (dev, CI). Disabled by default — enable
 * via {@code outbox.cleanup.spring-execution.enabled=true}.
 *
 * <p>This component only executes partition management. Observability is handled by {@link
 * OutboxCleanupObserver}.
 *
 * <p>The underlying PL/pgSQL function is idempotent, so concurrent execution from multiple
 * instances is safe but wasteful. Consider ShedLock if this scheduler is enabled in multi-replica
 * environments.
 */
@Component
@ConditionalOnProperty(name = "outbox.cleanup.spring-execution.enabled", havingValue = "true")
public class OutboxCleanupFallbackScheduler {

  private static final Logger log = LoggerFactory.getLogger(OutboxCleanupFallbackScheduler.class);

  private final DSLContext dslContext;
  private final int retentionDays;

  public OutboxCleanupFallbackScheduler(
      DSLContext dslContext, @Value("${outbox.cleanup.retention-days:3}") int retentionDays) {
    this.dslContext = dslContext;
    this.retentionDays = retentionDays;
  }

  @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
  public void cleanupPartitions() {
    log.info("Starting outbox partition cleanup with retention={} days", retentionDays);
    try {
      manageOutboxPartitions(dslContext.configuration(), retentionDays);
      log.info("Outbox partition cleanup completed successfully");
    } catch (DataAccessException e) {
      log.error("Outbox partition cleanup failed", e);
    }
  }
}
