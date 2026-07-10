package io.taanielo.jmud.core.guild;

/**
 * Thrown when guild data cannot be read from the underlying storage at startup.
 */
public class GuildRepositoryException extends Exception {

    public GuildRepositoryException(String message) {
        super(message);
    }

    public GuildRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
