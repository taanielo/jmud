/**
 * Player-funded mob bounties: a player escrows gold against a mob <em>type</em> (template), payable
 * to whoever next kills a mob of that type anywhere in the world.
 *
 * <p>{@code BOUNTY POST} escrows gold from the poster's on-hand balance into a persisted
 * {@link io.taanielo.jmud.core.bounty.Bounty} record (never onto the {@code Player} schema); a second
 * player posting on the same mob type stacks as a separate backer, so a type's total reward is the
 * sum of every open backer's stake. {@code BOUNTY CANCEL} refunds the poster's own unclaimed stake in
 * full. When any player kills a bountied mob type, every open backer's stake pays out to the killer
 * (split across the eligible party exactly like a mob's gold drop), a server-wide announcement names
 * the killer, mob, and total, and the paid entries close.
 *
 * <p>All gold and bounty-state mutation happens on the tick thread as part of the invoking player's
 * queued command or the mob-death reward path (AGENTS.md §5); no gold is ever created or destroyed by
 * a post, cancel, or payout. The active-bounty set is held in memory and mirrored to
 * {@code data/world-state/bounties.json} write-behind so the hot mob-death path never blocks on disk.
 */
@NullMarked
package io.taanielo.jmud.core.bounty;

import org.jspecify.annotations.NullMarked;
