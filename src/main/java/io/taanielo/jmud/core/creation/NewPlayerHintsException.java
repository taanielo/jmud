package io.taanielo.jmud.core.creation;

/**
 * Thrown when the new-player hints definition cannot be loaded or is invalid.
 */
public class NewPlayerHintsException extends Exception {

    public NewPlayerHintsException(String message) {
        super(message);
    }

    public NewPlayerHintsException(String message, Throwable cause) {
        super(message, cause);
    }
}
