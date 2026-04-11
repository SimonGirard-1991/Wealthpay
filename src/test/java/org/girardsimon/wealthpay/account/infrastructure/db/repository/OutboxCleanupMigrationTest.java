package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.girardsimon.wealthpay.account.jooq.Tables.OUTBOX_CLEANUP_LOG;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

class OutboxCleanupMigrationTest extends AbstractContainerTest {

  @Test
  void v16_should_backfill_started_and_completed_at_from_run_at_on_upgrade() throws SQLException {
    Flyway baseline = flyway("15");
    baseline.clean();
    baseline.migrate();

    OffsetDateTime historicalRunAt = OffsetDateTime.of(2026, 4, 1, 3, 0, 0, 0, ZoneOffset.UTC);

    try (Connection connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      DSLContext dsl = DSL.using(connection);
      dsl.insertInto(
              OUTBOX_CLEANUP_LOG,
              OUTBOX_CLEANUP_LOG.RUN_AT,
              OUTBOX_CLEANUP_LOG.PARTITIONS_CREATED,
              OUTBOX_CLEANUP_LOG.PARTITIONS_DROPPED,
              OUTBOX_CLEANUP_LOG.REMAINING_PARTITIONS)
          .values(historicalRunAt, 8, 1, 7)
          .execute();
    }

    flyway("16").migrate();

    try (Connection connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      DSLContext dsl = DSL.using(connection);
      Record3<String, OffsetDateTime, OffsetDateTime> record3 =
          dsl.select(
                  DSL.field("status", String.class),
                  DSL.field("started_at", OffsetDateTime.class),
                  DSL.field("completed_at", OffsetDateTime.class))
              .from("account.outbox_cleanup_log")
              .fetchOne();

      assertThat(record3).isNotNull();
      assertThat(record3.value1()).isEqualTo("success");
      assertThat(record3.value2()).isEqualTo(historicalRunAt);
      assertThat(record3.value3()).isEqualTo(historicalRunAt);
    }
  }

  private Flyway flyway(String targetVersion) {
    return Flyway.configure()
        .cleanDisabled(false)
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration/account")
        .defaultSchema("account")
        .schemas("account")
        .target(targetVersion)
        .load();
  }
}
