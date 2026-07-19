package io.taanielo.jmud.core.creation;

/**
 * Loads the {@link NewPlayerHints} onboarding hint block shown to new characters at creation.
 *
 * <p>The definition is game content stored as versioned JSON under {@code data/}; the concrete
 * implementation lives in the infrastructure layer (AGENTS.md §3.2, §11).
 */
public interface NewPlayerHintsRepository {

    /**
     * Loads the configured new-player hints block.
     *
     * @return the hints definition
     * @throws NewPlayerHintsException if the hints data is missing, unreadable, or invalid
     */
    NewPlayerHints load() throws NewPlayerHintsException;
}
