/**
 * Salvage domain: breaking down unwanted equippable gear back into crafting materials at a
 * blacksmith via the SALVAGE command, closing the loop between the loot system and the crafting
 * system.
 *
 * <p>A {@link io.taanielo.jmud.core.salvage.SalvageTier} maps an item
 * {@link io.taanielo.jmud.core.world.Rarity rarity tier} to the
 * {@link io.taanielo.jmud.core.salvage.SalvageMaterial materials} salvaging an item of that tier
 * yields. The {@link io.taanielo.jmud.core.salvage.SalvageService} is a pure application service over
 * immutable {@link io.taanielo.jmud.core.player.Player} values — it never mutates its inputs and
 * returns an updated player copy on success, mirroring
 * {@link io.taanielo.jmud.core.craft.CraftingService}. All mutation is applied by the caller on the
 * tick thread (AGENTS.md §5); tier data is loaded once at startup through
 * {@link io.taanielo.jmud.core.salvage.SalvageTierRepository}, so no blocking I/O reaches the tick
 * loop. NullAway-checked ({@code @NullMarked}) since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.salvage;

import org.jspecify.annotations.NullMarked;
