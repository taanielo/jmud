package io.taanielo.jmud.core.achievement;

/**
 * Thrown when {@link Achievement} definitions cannot be loaded from their backing store.
 */
public class AchievementRepositoryException extends Exception {

    public AchievementRepositoryException(String message) {
        super(message);
    }

    public AchievementRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
