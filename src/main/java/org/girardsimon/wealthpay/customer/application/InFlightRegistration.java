package org.girardsimon.wealthpay.customer.application;

/**
 * The key is taken, yet no committed row is visible. Reachable only through a silent regression: an
 * isolation level above READ COMMITTED, a job pruning the idempotency table, or an adapter that
 * drops the separate fallback {@code SELECT} after the claim-or-replay CTE.
 *
 * <p>Count it and alert above zero - mapped to a bare conflict it is indistinguishable from
 * ordinary contention on every dashboard. Map it to a conflict the client may retry, never to a
 * null dereference.
 */
public record InFlightRegistration() implements RegistrationOutcome {}
