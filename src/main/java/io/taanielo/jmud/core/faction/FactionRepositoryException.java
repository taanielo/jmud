package io.taanielo.jmud.core.faction;

/**
 * Thrown when faction definitions cannot be loaded from the data store.
 */
public class FactionRepositoryException extends Exception {

    public FactionRepositoryException(String message) {
        super(message);
    }

    public FactionRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
