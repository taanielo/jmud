/**
 * Resource-gathering domain: harvesting raw crafting materials from world resource nodes (ore
 * veins, herb patches) via the {@code GATHER} command.
 *
 * <p>A {@link io.taanielo.jmud.core.gathering.ResourceNode} is an immutable definition tying a raw
 * material item to a room, with a fixed respawn delay measured in ticks. The mutable depletion state
 * (which nodes are currently harvested and how long until they respawn) is owned by the
 * {@link io.taanielo.jmud.core.gathering.ResourceGatheringService}, mirroring the
 * {@code Corpse}/{@code RoomItemService} split. The service is a pure application service over
 * immutable {@link io.taanielo.jmud.core.player.Player} values — it never mutates its inputs and
 * returns an updated player copy on a successful harvest, mirroring
 * {@link io.taanielo.jmud.core.craft.CraftingService}.
 *
 * <p>All harvest and respawn mutation happens on the tick thread (AGENTS.md §5): harvests arrive
 * through the per-player command queue and respawns are driven by
 * {@link io.taanielo.jmud.core.gathering.ResourceNodeRespawnTicker}. Node state is transient and not
 * persisted, the same tradeoff mob respawns already make. Node definitions are loaded once at startup
 * through {@link io.taanielo.jmud.core.gathering.ResourceNodeRepository}, so no blocking I/O reaches
 * the tick loop. NullAway-checked ({@code @NullMarked}) since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.gathering;

import org.jspecify.annotations.NullMarked;
