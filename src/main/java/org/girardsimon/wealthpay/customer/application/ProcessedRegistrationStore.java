package org.girardsimon.wealthpay.customer.application;

import java.time.Instant;
import org.girardsimon.wealthpay.customer.domain.model.CustomerId;

/** Registration idempotency: which attempt owns a client-supplied key. */
public interface ProcessedRegistrationStore {

  /**
   * Whether some committed attempt already holds this key.
   *
   * <p>A fast path only, so that a retry does not pay for a fresh admission evaluation and the
   * unconditionally-committed decision record that comes with it. Existence is all it may report:
   * {@link #register} stays the authority, and it is what detects a changed payload reusing the
   * key.
   */
  boolean isClaimed(IdempotencyKey idempotencyKey);

  /**
   * Claims {@code idempotencyKey} for this attempt in a single round trip, storing {@code
   * customerId} so that a replay can re-read the customer and rebuild the identical response.
   *
   * <p>A key already held by a <em>different</em> payload is a conflict, not an outcome, and is
   * raised rather than returned - as the customer counterpart of the account BC's {@code
   * TransactionIdConflictException}, introduced with the adapter that first throws it.
   */
  RegistrationOutcome register(
      IdempotencyKey idempotencyKey,
      Fingerprint fingerprint,
      CustomerId customerId,
      Instant occurredAt);
}
