package io.taanielo.jmud.core.world.repository;

/**
 * Canonical checked exception thrown when a repository fails to read or
 * write persistent data (e.g. an underlying {@link java.io.IOException}).
 *
 * <p>This is the shared repository exception type for the codebase. Despite
 * living in the {@code world.repository} package for historical reasons, it
 * is not scoped to world/room/item data — new and migrated repositories
 * across all domains should throw this type rather than defining a new
 * per-domain {@code *RepositoryException} clone.
 */
public class RepositoryException extends Exception {
    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
