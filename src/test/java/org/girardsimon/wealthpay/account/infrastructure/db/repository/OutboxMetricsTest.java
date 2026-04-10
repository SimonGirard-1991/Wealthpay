package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;

@JooqTest
@Import({OutboxMetrics.class, SimpleMeterRegistry.class})
class OutboxMetricsTest extends AbstractContainerTest {

  @Autowired private MeterRegistry meterRegistry;

  @Test
  void gauge_should_be_registered_and_return_non_negative_value() {
    // Assert
    Gauge gauge = meterRegistry.find("outbox.row_count.estimate").gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isGreaterThanOrEqualTo(0.0);
  }
}
