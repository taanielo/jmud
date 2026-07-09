package io.taanielo.jmud.core.notes.repository.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a single room's board file ({@code data/boards/<room-id>.json}).
 *
 * @param schemaVersion the file schema version
 * @param notes         the notes pinned to the room, oldest first
 */
record RoomNotesDto(
    int schemaVersion,
    @Nullable List<NoteDto> notes
) {
}
