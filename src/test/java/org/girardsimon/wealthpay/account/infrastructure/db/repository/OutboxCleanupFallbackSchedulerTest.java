package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.girardsimon.wealthpay.account.jooq.Tables.OUTBOX_CLEANUP_LOG;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@JooqTest
@Import(OutboxCleanupFallbackScheduler.class)
@TestPropertySource(properties = "outbox.cleanup.spring-execution.enabled=true")
class OutboxCleanupFallbackSchedulerTest extends AbstractContainerTest {

  @Autowired private DSLContext dsl;
  @Autowired private OutboxCleanupFallbackScheduler scheduler;

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

    assertThat(partitionCount).isGreaterThanOrEqualTo(8);
  }

  @Test
  void cleanupPartitions_should_log_successful_run_in_cleanup_log() {
    // Act
    scheduler.cleanupPartitions();

    // Assert — verify a row was logged with status = 'success'
    String status =
        dsl.select(OUTBOX_CLEANUP_LOG.STATUS)
            .from(OUTBOX_CLEANUP_LOG)
            .orderBy(OUTBOX_CLEANUP_LOG.RUN_AT.desc())
            .limit(1)
            .fetchOneInto(String.class);

    assertThat(status).isEqualTo("success");
  }

  @Test
  void cleanupPartitions_should_not_throw_on_failure() {
    // Arrange — drop the function so the scheduler call fails
    dsl.execute("DROP FUNCTION account.manage_outbox_partitions(int)");

    // Act — must not throw (a @Scheduled method that throws kills the scheduler thread)
    assertThatCode(() -> scheduler.cleanupPartitions()).doesNotThrowAnyException();
  }
}
