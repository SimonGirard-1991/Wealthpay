package org.girardsimon.wealthpay.customer.application;

import java.time.Instant;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionDecision;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionSubject;

/**
 * Persists admission decisions. Separate from {@link CustomerStore} because it commits on its own:
 * a refusal aborts the registration, and a decision written in that transaction would roll back
 * with it, destroying the only record of why someone was refused.
 *
 * <p><strong>The calling method must not be {@code @Transactional}</strong>, and must not itself be
 * a {@code REQUIRES_NEW} method reached by self-invocation - self-invocation is not proxied. Either
 * way the write joins the caller's transaction and rolls back with it, with no exception, no metric
 * and no log. Use a separate collaborator bean or a {@code TransactionTemplate}.
 */
public interface AdmissionDecisionRecorder {

  /**
   * Commits unconditionally, refusals included, and returns the id an admitted decision is later
   * anchored to.
   *
   * <p>The decision holds no customer id on any path; the idempotency key is the correlation handle
   * back to the registration attempt.
   */
  AdmissionDecisionId recordDecision(
      IdempotencyKey idempotencyKey,
      AdmissionSubject subject,
      AdmissionDecision decision,
      Instant decidedAt);
}
