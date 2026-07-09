package io.taanielo.jmud.core.notes;

import java.util.UUID;

/**
 * Opaque identifier for a single {@link PlayerNote}.
 *
 * <p>Values are stable strings (UUIDs for freshly created notes) so a note can be referenced across
 * persistence boundaries independent of its display position on the board.
 *
 * @param value the non-blank identifier string
 */
public record NoteId(String value) {

    /**
     * Creates a note id, rejecting blank values.
     *
     * @param value the identifier string
     */
    public NoteId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Note id must not be blank");
        }
    }

    /**
     * Wraps an existing identifier string.
     *
     * @param value the identifier string
     * @return the note id
     */
    public static NoteId of(String value) {
        return new NoteId(value);
    }

    /**
     * Generates a fresh, globally unique note id.
     *
     * @return a new random note id
     */
    public static NoteId random() {
        return new NoteId(UUID.randomUUID().toString());
    }
}
