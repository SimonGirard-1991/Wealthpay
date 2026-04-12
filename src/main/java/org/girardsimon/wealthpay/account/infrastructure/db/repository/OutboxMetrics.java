package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

  private static final Logger log = LoggerFactory.getLogger(OutboxMetrics.class);

  private static final String ESTIMATE_QUERY =
      """
      SELECT COALESCE(SUM(GREATEST(child.reltuples, 0)), 0)::bigint
      FROM pg_inherits i
      JOIN pg_class parent ON parent.oid = i.inhparent
      JOIN pg_class child  ON child.oid  = i.inhrelid
      JOIN pg_namespace n  ON n.oid      = parent.relnamespace
      WHERE n.nspname    = 'account'
        AND parent.relname = 'outbox'
      """;

  private final DSLContext dslContext;
  private final MeterRegistry meterRegistry;

  public OutboxMetrics(DSLContext dslContext, MeterRegistry meterRegistry) {
    this.dslContext = dslContext;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  void registerGauges() {
    Gauge.builder("outbox.row_count.estimate", this, OutboxMetrics::fetchEstimate)
        .description("Estimated row count of the account.outbox table")
        .register(meterRegistry);
  }

  private double fetchEstimate() {
    try {
      return Optional.ofNullable(dslContext.fetchOne(ESTIMATE_QUERY))
          .map(row -> row.get(0, Number.class))
          .map(Number::doubleValue)
          .orElse(0.0);
    } catch (DataAccessException e) {
      log.warn("Failed to fetch outbox row count estimate", e);
      return 0.0;
    }
  }
}
