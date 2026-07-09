package io.taanielo.jmud.core.notes.repository.json;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a single note, matching {@code docs/schemas/note.v1.json}.
 *
 * @param id        the unique note id
 * @param author    the author's username
 * @param content   the note body text
 * @param timestamp the ISO-8601 instant the note was posted
 * @param roomId    the id of the room the note is pinned to
 */
record NoteDto(
    @Nullable String id,
    @Nullable String author,
    @Nullable String content,
    @Nullable String timestamp,
    @Nullable String roomId
) {
}
