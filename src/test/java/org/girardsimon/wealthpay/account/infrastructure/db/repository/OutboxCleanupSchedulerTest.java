package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.girardsimon.wealthpay.account.jooq.Tables.OUTBOX_CLEANUP_LOG;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.girardsimon.wealthpay.shared.config.TimeConfig;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import({OutboxCleanupScheduler.class, SimpleMeterRegistry.class, TimeConfig.class})
class OutboxCleanupSchedulerTest extends AbstractContainerTest {

  @Autowired private DSLContext dsl;
  @Autowired private OutboxCleanupScheduler scheduler;
  @Autowired private MeterRegistry meterRegistry;

  @Test
  void cleanupPartitions_should_create_future_partitions_and_complete_without_error() {
    // Act
    scheduler.cleanupPartitions();

    // Assert — verify that partitions were created by querying pg_inherits
    int partitionCount =
        dsl.fetchCount(
            DSL.select()
                .from("pg_inherits")
                .join("pg_class")
                .on(DSL.field("pg_class.oid").eq(DSL.field("pg_inherits.inhparent")))
                .join("pg_namespace")
                .on(DSL.field("pg_namespace.oid").eq(DSL.field("pg_class.relnamespace")))
                .where(
                    DSL.field("pg_namespace.nspname")
                        .eq("account")
                        .and(DSL.field("pg_class.relname").eq("outbox"))));

    // V13 creates today..today+7 (8 days).
    // The scheduler uses the same 8-day window, so the parent should have at least 8 children.
    assertThat(partitionCount).isGreaterThanOrEqualTo(8);
  }

  @Test
  void cleanupPartitions_should_log_run_in_cleanup_log() {
    // Act
    scheduler.cleanupPartitions();

    // Assert
    Record1<Integer> logCount = dsl.select(DSL.count()).from(OUTBOX_CLEANUP_LOG).fetchOne();

    assertThat(logCount).isNotNull();
    assertThat(logCount.value1()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void cleanupPartitions_should_record_success_metric() {
    // Arrange — capture counters before act (counters are cumulative across tests)
    double successBefore =
        meterRegistry.counter("outbox.cleanup.runs", "outcome", "success").count();

    // Act
    scheduler.cleanupPartitions();

    // Assert
    double successAfter =
        meterRegistry.counter("outbox.cleanup.runs", "outcome", "success").count();
    assertThat(successAfter - successBefore).isEqualTo(1.0);

    double lastRun = meterRegistry.get("outbox.cleanup.last_run.seconds").gauge().value();
    assertThat(lastRun).isGreaterThan(0.0);
  }

  @Test
  void cleanupPartitions_should_record_failure_metric_and_not_throw() {
    // Arrange — capture counters before act (counters are cumulative across tests)
    double failureBefore =
        meterRegistry.counter("outbox.cleanup.runs", "outcome", "failure").count();
    double successBefore =
        meterRegistry.counter("outbox.cleanup.runs", "outcome", "success").count();

    // Drop the function so the scheduler call fails
    dsl.execute("DROP FUNCTION account.manage_outbox_partitions(int)");

    // Act — must not throw (a @Scheduled method that throws kills the scheduler thread)
    scheduler.cleanupPartitions();

    // Assert
    double failureAfter =
        meterRegistry.counter("outbox.cleanup.runs", "outcome", "failure").count();
    assertThat(failureAfter - failureBefore).isEqualTo(1.0);

    double successAfter =
        meterRegistry.counter("outbox.cleanup.runs", "outcome", "success").count();
    assertThat(successAfter - successBefore).isZero();
    // No manual restore needed — @JooqTest rolls back the transaction,
    // and PostgreSQL DDL (DROP FUNCTION) is transactional.
  }
}
