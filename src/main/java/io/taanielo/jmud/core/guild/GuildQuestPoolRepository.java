package io.taanielo.jmud.core.guild;

/**
 * Persistence port for the cooperative guild-quest pool.
 *
 * <p>Implementations load the level-banded catalogue of assignable {@link GuildQuestObjective}s from
 * versioned JSON data ({@code data/quests/guild/*.json}) once at startup. The pool is static game
 * content (AGENTS.md §11); per-guild progress lives on the {@link Guild} instead.
 */
public interface GuildQuestPoolRepository {

    /**
     * Loads the guild-quest pool.
     *
     * @return the assembled pool of guild-quest objectives
     * @throws GuildRepositoryException when the guild-quest data cannot be read or is invalid
     */
    GuildQuestPool load() throws GuildRepositoryException;
}
