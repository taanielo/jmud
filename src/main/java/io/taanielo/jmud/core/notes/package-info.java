/**
 * Bulletin-board (notes) domain: player-authored {@link io.taanielo.jmud.core.notes.PlayerNote}
 * messages pinned to a specific room, the {@link io.taanielo.jmud.core.notes.NotesRepository}
 * persistence port, and the {@link io.taanielo.jmud.core.notes.NotesService} that owns the
 * in-memory per-room note lists and enforces posting/deletion rules.
 *
 * <p>All note state is mutated only on the tick thread via the player command queue (AGENTS.md §5);
 * the {@link io.taanielo.jmud.core.notes.NotesService} keeps the authoritative in-memory state and
 * hands immutable snapshots to the repository for write-behind persistence, so no blocking disk I/O
 * is reachable from {@code tick()}. Note timestamps are descriptive metadata sourced from an
 * injected {@link java.time.Clock} so they stay testable. NullAway-checked ({@code @NullMarked})
 * since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.notes;

import org.jspecify.annotations.NullMarked;
