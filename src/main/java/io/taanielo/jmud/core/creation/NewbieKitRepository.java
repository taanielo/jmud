package io.taanielo.jmud.core.creation;

/**
 * Loads the {@link NewbieKit} starting-kit definition granted to new characters at creation.
 *
 * <p>The definition is game content stored as versioned JSON under {@code data/}; the concrete
 * implementation lives in the infrastructure layer (AGENTS.md §3.2, §11).
 */
public interface NewbieKitRepository {

    /**
     * Loads the configured newbie starting kit.
     *
     * @return the starting kit definition
     * @throws NewbieKitException if the kit data is missing, unreadable, or invalid
     */
    NewbieKit load() throws NewbieKitException;
}
