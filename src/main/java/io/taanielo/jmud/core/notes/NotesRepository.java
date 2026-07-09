package io.taanielo.jmud.core.notes;

import java.util.List;
import java.util.Map;

import io.taanielo.jmud.core.world.RoomId;

/**
 * Persistence port for room bulletin-board notes.
 *
 * <p>Implementations load all persisted notes once at startup and persist per-room note lists
 * without blocking the tick thread (AGENTS.md §5). The domain {@link NotesService} owns the
 * authoritative in-memory state; this port only reads it at boot and writes snapshots back.
 */
public interface NotesRepository {

    /**
     * Loads every persisted note, grouped by the room its board belongs to.
     *
     * <p>Called once at startup on the bootstrap thread. The returned lists are ordered oldest note
     * first.
     *
     * @return a map from room id to that room's notes (never {@code null})
     */
    Map<RoomId, List<PlayerNote>> loadAll();

    /**
     * Persists the complete set of notes for a single room, replacing any previously stored notes
     * for that room. An empty list removes the room's stored board.
     *
     * <p>Implementations must perform the actual disk write off the tick thread (write-behind), so
     * this call is safe to invoke during command execution.
     *
     * @param roomId the room whose board to persist
     * @param notes  the full, current list of notes for that room
     */
    void saveRoom(RoomId roomId, List<PlayerNote> notes);
}
