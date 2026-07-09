package io.taanielo.jmud.core.notes;

/**
 * Thrown when bulletin-board notes cannot be loaded from or persisted to storage.
 */
public class NotesRepositoryException extends RuntimeException {

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message
     */
    public NotesRepositoryException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public NotesRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
