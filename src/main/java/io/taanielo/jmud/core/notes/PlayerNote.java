package io.taanielo.jmud.core.notes;

import java.time.Instant;
import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;

/**
 * An immutable player-authored note pinned to a room's bulletin board.
 *
 * @param id        the unique note identifier
 * @param author    the username of the player who posted the note
 * @param content   the note body text
 * @param timestamp the wall-clock instant the note was posted (descriptive metadata only)
 * @param roomId    the room whose board the note is pinned to
 */
public record PlayerNote(NoteId id, Username author, String content, Instant timestamp, RoomId roomId) {

    /**
     * Validates the note, rejecting null components and blank content.
     */
    public PlayerNote {
        Objects.requireNonNull(id, "Note id is required");
        Objects.requireNonNull(author, "Note author is required");
        Objects.requireNonNull(timestamp, "Note timestamp is required");
        Objects.requireNonNull(roomId, "Note room id is required");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Note content must not be blank");
        }
    }

    /**
     * Returns whether the given player authored this note.
     *
     * @param username the username to test
     * @return {@code true} if {@code username} matches the note's author
     */
    public boolean isAuthoredBy(Username username) {
        return author.equals(username);
    }
}
