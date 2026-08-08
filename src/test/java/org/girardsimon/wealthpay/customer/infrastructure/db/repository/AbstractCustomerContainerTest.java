package org.girardsimon.wealthpay.customer.infrastructure.db.repository;

import org.girardsimon.wealthpay.customer.infrastructure.db.CustomerFlywayConfig;
import org.girardsimon.wealthpay.testsupport.AbstractContainerTest;
import org.springframework.context.annotation.Import;

/**
 * Migrates the {@code customer} schema through the same bean production uses, so a test never
 * exercises a schema selection the application does not.
 *
 * <p>The import is what makes this necessary rather than decorative: container tests are
 * {@code @JooqTest} slices, which do not component-scan, so without it Flyway auto-configuration
 * stays in charge of the test path — reachable in the slice via {@code @AutoConfigureJooq} — and
 * quietly migrates {@code classpath:db/migration} recursively, merging both bounded contexts into
 * one history. That surfaces as "found more than one migration with version 1", which points
 * nowhere near the missing annotation.
 *
 * <p>Per bounded context rather than on the neutral base, so that a broken {@code customer}
 * migration fails only the {@code customer} suite and {@code customer} tests do not pay for the
 * {@code account} migrations.
 */
@Import(CustomerFlywayConfig.class)
public abstract class AbstractCustomerContainerTest extends AbstractContainerTest {}
