package io.taanielo.jmud.core.authentication;

/**
 * Thrown when a {@link UserRegistry} operation fails due to an I/O or
 * persistence error.
 */
public class UserRegistryException extends Exception {

    public UserRegistryException(String message) {
        super(message);
    }

    public UserRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
