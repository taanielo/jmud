/**
 * JSON persistence for the open-bounty ledger ({@code data/world-state/bounties.json}).
 *
 * <p>The authoritative set of open bounties is held in memory so the tick-thread payout check
 * ({@code BountyService#claim}) never reads from disk; changes are mirrored to the JSON file
 * write-behind on a dedicated virtual thread, mirroring {@code JsonDiscoveredExitsRepository}.
 */
@NullMarked
package io.taanielo.jmud.core.bounty.repository.json;

import org.jspecify.annotations.NullMarked;
