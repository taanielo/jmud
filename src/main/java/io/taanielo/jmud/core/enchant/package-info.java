/**
 * Enchanting domain: permanently imbuing a carried, equippable item with an additional stat affix
 * at an Enchanter NPC (the ENCHANT command). Unlike {@code core.craft}, which produces a brand new
 * output item, enchanting mutates an existing inventory item instance in place (returning a new
 * immutable {@link io.taanielo.jmud.core.world.Item} copy), consuming the recipe's materials and
 * gold. Recipes reference an existing affix id from {@code data/item-affixes.json} rather than
 * inventing a parallel stat system.
 */
@NullMarked
package io.taanielo.jmud.core.enchant;

import org.jspecify.annotations.NullMarked;
