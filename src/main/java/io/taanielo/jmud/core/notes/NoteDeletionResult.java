package io.taanielo.jmud.core.notes;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of a {@link NotesService#deleteNote} attempt.
 *
 * @param outcome the categorised result
 * @param removed the note that was removed, present only when {@code outcome} is
 *                {@link Outcome#DELETED}
 */
public record NoteDeletionResult(Outcome outcome, @Nullable PlayerNote removed) {

    /** Possible results of a delete attempt. */
    public enum Outcome {
        /** The note was removed. */
        DELETED,
        /** No note exists at the requested position. */
        NO_SUCH_NOTE,
        /** The requester is neither the author nor otherwise permitted to delete the note. */
        NOT_AUTHORIZED
    }

    /**
     * Creates a successful deletion result.
     *
     * @param removed the removed note
     * @return the result
     */
    public static NoteDeletionResult deleted(PlayerNote removed) {
        return new NoteDeletionResult(Outcome.DELETED, removed);
    }

    /**
     * Creates a "no such note" result.
     *
     * @return the result
     */
    public static NoteDeletionResult noSuchNote() {
        return new NoteDeletionResult(Outcome.NO_SUCH_NOTE, null);
    }

    /**
     * Creates a "not authorized" result.
     *
     * @return the result
     */
    public static NoteDeletionResult notAuthorized() {
        return new NoteDeletionResult(Outcome.NOT_AUTHORIZED, null);
    }

    /**
     * Returns whether the note was successfully deleted.
     *
     * @return {@code true} when the outcome is {@link Outcome#DELETED}
     */
    public boolean success() {
        return outcome == Outcome.DELETED;
    }
}
