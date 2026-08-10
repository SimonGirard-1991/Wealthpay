package org.girardsimon.wealthpay.customer.application;

/**
 * The key is taken, yet no committed row is visible.
 *
 * <p>Unreachable only if the implementation follows the claim-or-replay CTE with a
 * <em>separate</em> fallback {@code SELECT}. A single data-modifying CTE is not enough: every part
 * of it runs on one snapshot, so under two concurrent attempts the insert half blocks, skips once
 * the winner commits, and the read half - still on the pre-commit snapshot - finds nothing either.
 * Both halves empty, and this becomes the <em>normal</em> outcome of a double submit rather than a
 * defensive one. The second statement takes a fresh snapshot and sees the committed winner.
 *
 * <p>So it is kept for the cases that remain: a configured isolation level above READ COMMITTED, a
 * job pruning the idempotency table, or an implementation that drops that fallback. Each is a
 * silent config or deploy regression that nothing else catches, so this must be <em>counted</em>
 * and alerted on above zero - mapped to a bare conflict it is indistinguishable from ordinary
 * contention on every dashboard. The right threshold for a state that cannot occur is zero.
 *
 * <p>Map it to a conflict the client may retry - every cause above is transient per transaction,
 * since a fresh attempt takes a fresh snapshot - and never to a null dereference.
 */
public record InFlightRegistration() implements RegistrationOutcome {}
