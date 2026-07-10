/**
 * Hot-reload of JSON game content (rooms, items, mob templates) for the wizard {@code RELOAD}
 * command (issue #349).
 *
 * <p>The design separates a tick-thread-free <em>prepare</em> phase — reading and validating every
 * backing JSON file into an in-memory snapshot without touching live game state — from an atomic
 * <em>commit</em> phase that swaps the repository caches on the tick thread (AGENTS.md §5). If any
 * file fails to parse or validate during prepare, the operation aborts before any commit, so live
 * content is never left in a partial state.
 */
@NullMarked
package io.taanielo.jmud.core.reload;

import org.jspecify.annotations.NullMarked;
