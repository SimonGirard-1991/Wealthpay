package org.girardsimon.wealthpay.customer.application;

import java.time.Instant;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionDecision;
import org.girardsimon.wealthpay.customer.domain.model.AdmissionSubject;

/**
 * Persists admission decisions. Separate from {@link CustomerStore} because it commits on its own:
 * a refusal aborts the registration, and a decision written in that transaction would roll back
 * with it, destroying the only record of why someone was refused.
 *
 * <p><strong>The calling method must not be {@code @Transactional}</strong>, and this must not be
 * reached by a {@code REQUIRES_NEW} method invoked on {@code this} - self-invocation is not
 * proxied, so it silently joins the caller's transaction. Either mistake reinstates the rollback
 * with no exception, no metric and no log; it surfaces years later as a refusal that cannot be
 * evidenced. Use a separate collaborator bean or an explicit {@code TransactionTemplate}.
 */
public interface AdmissionDecisionRecorder {

  /**
   * Records the decision, the inputs it was evaluated against and every match it carries, and
   * returns the id an admitted decision is later anchored to. Commits unconditionally, refusals
   * included.
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
