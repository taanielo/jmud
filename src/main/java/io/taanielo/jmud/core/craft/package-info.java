/**
 * Crafting domain: turning gathered materials (mob trophy drops such as Wolf Pelts and Troll Teeth)
 * plus gold into upgraded gear via a blacksmith.
 *
 * <p>A {@link io.taanielo.jmud.core.craft.Recipe} names an output item id, a gold cost, and the
 * {@link io.taanielo.jmud.core.craft.RecipeMaterial materials} it consumes. The
 * {@link io.taanielo.jmud.core.craft.CraftingService} is a pure application service over immutable
 * {@link io.taanielo.jmud.core.player.Player} values — it never mutates its inputs and returns an
 * updated player copy on success, mirroring {@link io.taanielo.jmud.core.shop.ShopService}. All
 * mutation is applied by the caller on the tick thread (AGENTS.md §5); recipe data is loaded once at
 * startup through {@link io.taanielo.jmud.core.craft.RecipeRepository}, so no blocking I/O reaches
 * the tick loop. NullAway-checked ({@code @NullMarked}) since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.craft;

import org.jspecify.annotations.NullMarked;
