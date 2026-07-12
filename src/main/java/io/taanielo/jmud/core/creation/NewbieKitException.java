package io.taanielo.jmud.core.creation;

/**
 * Thrown when the newbie starting-kit definition cannot be loaded or is invalid.
 */
public class NewbieKitException extends Exception {

    public NewbieKitException(String message) {
        super(message);
    }

    public NewbieKitException(String message, Throwable cause) {
        super(message, cause);
    }
}
