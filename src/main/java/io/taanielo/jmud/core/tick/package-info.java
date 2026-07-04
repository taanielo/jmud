/**
 * Tick loop scheduling: the single-writer game loop that drains {@link
 * io.taanielo.jmud.core.tick.Tickable}s at a fixed interval. NullAway-checked ({@code
 * @NullMarked}) as the starting ratchet for nullness enforcement.
 */
@NullMarked
package io.taanielo.jmud.core.tick;

import org.jspecify.annotations.NullMarked;
