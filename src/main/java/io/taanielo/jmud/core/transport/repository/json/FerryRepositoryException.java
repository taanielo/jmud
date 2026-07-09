package io.taanielo.jmud.core.transport.repository.json;

/**
 * Thrown when ferry definition data cannot be read or is malformed.
 */
public class FerryRepositoryException extends RuntimeException {

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message
     */
    public FerryRepositoryException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public FerryRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
