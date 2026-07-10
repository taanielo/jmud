/**
 * Infrastructure: file-backed loading of crafting recipe definitions from {@code data/recipes/*.json}.
 *
 * <p>{@link io.taanielo.jmud.core.craft.repository.json.JsonRecipeRepository} is constructed only by
 * the composition root ({@code GameContext}) per AGENTS.md §3.3 and eagerly caches every recipe at
 * startup, so no disk I/O ever reaches the tick loop. NullAway-checked ({@code @NullMarked}) since
 * this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.craft.repository.json;

import org.jspecify.annotations.NullMarked;
