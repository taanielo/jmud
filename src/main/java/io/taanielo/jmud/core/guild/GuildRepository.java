package io.taanielo.jmud.core.guild;

import java.util.List;

/**
 * Persistence port for {@link Guild} rosters.
 *
 * <p>Implementations load every guild once at startup ({@link #loadAll()}) and persist mutations
 * asynchronously ({@link #save(Guild)} / {@link #delete(GuildId)}) so the tick thread never blocks on
 * disk I/O (AGENTS.md §5). The in-memory authoritative copy lives in {@link GuildService}.
 */
public interface GuildRepository {

    /**
     * Loads every persisted guild.
     *
     * @return all guilds on disk
     * @throws GuildRepositoryException when data cannot be read
     */
    List<Guild> loadAll() throws GuildRepositoryException;

    /**
     * Persists the given guild, overwriting any previous state for the same id. Implementations may
     * perform the write asynchronously (write-behind).
     *
     * @param guild the guild snapshot to persist
     */
    void save(Guild guild);

    /**
     * Deletes the persisted record for the given guild id, if any. Implementations may perform the
     * deletion asynchronously (write-behind).
     *
     * @param guildId the id of the guild to delete
     */
    void delete(GuildId guildId);
}
