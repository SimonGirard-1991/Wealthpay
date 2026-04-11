package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.girardsimon.wealthpay.account.jooq.Tables.OUTBOX_CLEANUP_LOG;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import({OutboxCleanupObserver.class, SimpleMeterRegistry.class})
class OutboxCleanupObserverTest extends AbstractContainerTest {

  private static final String SUCCESS = "success";
  @Autowired private DSLContext dsl;
  @Autowired private OutboxCleanupObserver observer;
  @Autowired private MeterRegistry meterRegistry;

  @Test
  void pollCleanupStatus_should_update_last_run_gauge_from_latest_successful_row() {
    // Arrange
    OffsetDateTime successTime = OffsetDateTime.of(2026, 4, 10, 3, 0, 0, 0, ZoneOffset.UTC);
    dsl.insertInto(OUTBOX_CLEANUP_LOG)
        .set(OUTBOX_CLEANUP_LOG.PARTITIONS_CREATED, 8)
        .set(OUTBOX_CLEANUP_LOG.PARTITIONS_DROPPED, 0)
        .set(OUTBOX_CLEANUP_LOG.REMAINING_PARTITIONS, 8)
        .set(OUTBOX_CLEANUP_LOG.STATUS, SUCCESS)
        .set(OUTBOX_CLEANUP_LOG.STARTED_AT, successTime.minusSeconds(5))
        .set(OUTBOX_CLEANUP_LOG.COMPLETED_AT, successTime)
        .execute();

    // Act
    observer.pollCleanupStatus();

    // Assert
    Gauge gauge = meterRegistry.find("outbox.cleanup.last_run.seconds").gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo((double) successTime.toInstant().getEpochSecond());
  }

  @Test
  void pollCleanupStatus_should_report_status_1_when_latest_row_is_success() {
    // Arrange
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    dsl.insertInto(OUTBOX_CLEANUP_LOG)
        .set(OUTBOX_CLEANUP_LOG.PARTITIONS_CREATED, 8)
        .set(OUTBOX_CLEANUP_LOG.PARTITIONS_DROPPED, 0)
        .set(OUTBOX_CLEANUP_LOG.REMAINING_PARTITIONS, 8)
        .set(OUTBOX_CLEANUP_LOG.STATUS, SUCCESS)
        .set(OUTBOX_CLEANUP_LOG.STARTED_AT, now.minusSeconds(5))
        .set(OUTBOX_CLEANUP_LOG.COMPLETED_AT, now)
        .execute();

    // Act
    observer.pollCleanupStatus();

    // Assert
    Gauge gauge = meterRegistry.find("outbox.cleanup.last_status").gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo(1.0);
  }

  @Test
  void pollCleanupStatus_should_report_status_0_when_latest_row_is_failure() {
    // Arrange — older success, then newer failure
    OffsetDateTime successTime = OffsetDateTime.of(2026, 4, 9, 3, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime failureTime = OffsetDateTime.of(2026, 4, 10, 3, 0, 0, 0, ZoneOffset.UTC);

    dsl.insertInto(OUTBOX_CLEANUP_LOG)
        .set(OUTBOX_CLEANUP_LOG.PARTITIONS_CREATED, 8)
        .set(OUTBOX_CLEANUP_LOG.PARTITIONS_DROPPED, 0)
        .set(OUTBOX_CLEANUP_LOG.REMAINING_PARTITIONS, 8)
        .set(OUTBOX_CLEANUP_LOG.STATUS, SUCCESS)
        .set(OUTBOX_CLEANUP_LOG.STARTED_AT, successTime.minusSeconds(5))
        .set(OUTBOX_CLEANUP_LOG.COMPLETED_AT, successTime)
        .set(OUTBOX_CLEANUP_LOG.RUN_AT, successTime)
        .execute();

    dsl.insertInto(OUTBOX_CLEANUP_LOG)
        .set(OUTBOX_CLEANUP_LOG.PARTITIONS_CREATED, 0)
        .set(OUTBOX_CLEANUP_LOG.PARTITIONS_DROPPED, 0)
        .set(OUTBOX_CLEANUP_LOG.REMAINING_PARTITIONS, 0)
        .set(OUTBOX_CLEANUP_LOG.STATUS, "failure")
        .set(OUTBOX_CLEANUP_LOG.STARTED_AT, failureTime.minusSeconds(1))
        .set(OUTBOX_CLEANUP_LOG.COMPLETED_AT, failureTime)
        .set(OUTBOX_CLEANUP_LOG.ERROR_MESSAGE, "some error")
        .set(OUTBOX_CLEANUP_LOG.RUN_AT, failureTime)
        .execute();

    // Act
    observer.pollCleanupStatus();

    // Assert — last_status should be 0 (failure)
    Gauge statusGauge = meterRegistry.find("outbox.cleanup.last_status").gauge();
    assertThat(statusGauge).isNotNull();
    assertThat(statusGauge.value()).isEqualTo(0.0);

    // Assert — last_run should still reflect the older successful completion
    Gauge lastRunGauge = meterRegistry.find("outbox.cleanup.last_run.seconds").gauge();
    assertThat(lastRunGauge).isNotNull();
    assertThat(lastRunGauge.value()).isEqualTo((double) successTime.toInstant().getEpochSecond());
  }

  @Test
  void gauges_should_be_registered_at_construction_time() {
    // Assert — gauges exist immediately after context load
    Gauge lastRunGauge = meterRegistry.find("outbox.cleanup.last_run.seconds").gauge();
    assertThat(lastRunGauge).isNotNull();
    assertThat(lastRunGauge.value()).isGreaterThanOrEqualTo(0.0);

    // Default is 0.0 (unhealthy) on empty table — "never ran" should not look green
    Gauge statusGauge = meterRegistry.find("outbox.cleanup.last_status").gauge();
    assertThat(statusGauge).isNotNull();
    assertThat(statusGauge.value()).isEqualTo(0.0);
  }
}
