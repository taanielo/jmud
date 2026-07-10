/**
 * JSON transfer objects and mapping for crafting recipe files ({@code data/recipes/*.json}).
 *
 * <p>{@link io.taanielo.jmud.core.craft.dto.RecipeDto} mirrors the on-disk schema and is decoupled
 * from the {@link io.taanielo.jmud.core.craft.Recipe} domain value object by
 * {@link io.taanielo.jmud.core.craft.dto.RecipeMapper}, matching the {@code ItemDto}/{@code ItemMapper}
 * convention. NullAway-checked ({@code @NullMarked}) since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.craft.dto;

import org.jspecify.annotations.NullMarked;
