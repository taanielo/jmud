/**
 * JSON-backed infrastructure for loading {@link io.taanielo.jmud.core.gathering.ResourceNode}
 * definitions from {@code data/resource-nodes/*.json}.
 *
 * <p>Only the composition root ({@code GameContext}) constructs the repository here (AGENTS.md §3.3);
 * all content is read once at startup so no I/O reaches the tick loop. NullAway-checked
 * ({@code @NullMarked}) since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.gathering.repository.json;

import org.jspecify.annotations.NullMarked;
