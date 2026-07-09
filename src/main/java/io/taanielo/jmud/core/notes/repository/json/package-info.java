/**
 * Infrastructure adapter that persists room bulletin-board notes as JSON files under
 * {@code data/boards/<room-id>.json}, implementing the domain
 * {@link io.taanielo.jmud.core.notes.NotesRepository} port. Notes are loaded once at startup; writes
 * are performed by a dedicated write-behind virtual thread so the tick thread never touches disk
 * (AGENTS.md §5). Constructed only by the composition root (AGENTS.md §3.3). NullAway-checked
 * ({@code @NullMarked}) since this is a new package.
 */
@NullMarked
package io.taanielo.jmud.core.notes.repository.json;

import org.jspecify.annotations.NullMarked;
