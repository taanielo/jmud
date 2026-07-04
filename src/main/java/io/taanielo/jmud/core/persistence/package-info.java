/**
 * Write-behind persistence for game state (currently {@link io.taanielo.jmud.core.player.Player}
 * snapshots), so the tick thread never blocks on disk I/O (AGENTS.md §5). NullAway-checked
 * ({@code @NullMarked}) as part of the ongoing nullness enforcement ratchet.
 */
@NullMarked
package io.taanielo.jmud.core.persistence;

import org.jspecify.annotations.NullMarked;
