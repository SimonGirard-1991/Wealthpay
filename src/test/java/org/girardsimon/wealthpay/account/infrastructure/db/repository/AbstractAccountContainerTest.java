package org.girardsimon.wealthpay.account.infrastructure.db.repository;

import org.girardsimon.wealthpay.account.infrastructure.db.AccountFlywayConfig;
import org.girardsimon.wealthpay.testsupport.AbstractContainerTest;
import org.springframework.context.annotation.Import;

/**
 * Migrates the {@code account} schema through the same bean production uses. Lives inside the BC so
 * the neutral container base stays free of any bounded-context reference; each BC owns one of
 * these. {@code ConfigurationClassParser} walks the superclass chain, so subclasses declaring their
 * own {@code @Import} compose with this one rather than shadow it.
 */
@Import(AccountFlywayConfig.class)
public abstract class AbstractAccountContainerTest extends AbstractContainerTest {}
