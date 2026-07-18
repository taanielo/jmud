package io.taanielo.jmud.core.guild.repository.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a guild-quest pool file ({@code data/quests/guild/*.json}, schema version 1).
 *
 * <p>A pool lists the level-banded cooperative guild-quest objectives a guild's active guild quest is
 * drawn from. {@code type} is an optional human-readable discriminator ("guild"); the loader tolerates
 * unknown properties (AGENTS.md §11).
 *
 * @param schemaVersion the schema version, expected to be 1
 * @param type          optional human-readable discriminator ("guild")
 * @param name          optional display name of the pool
 * @param objectives    the list of guild-quest objectives, from low to high level band
 */
record GuildQuestPoolDto(
    int schemaVersion,
    @Nullable String type,
    @Nullable String name,
    @Nullable List<GuildQuestObjectiveDto> objectives
) {

    /**
     * JSON transfer object for a single guild-quest objective within a pool.
     *
     * @param id            unique objective id across the pool
     * @param name          display name shown to guild members
     * @param targetMobId   mob template id that must be killed to progress
     * @param targetName    plural display noun used in progress lines (e.g. "dire wolves")
     * @param requiredKills number of kills needed to complete the objective
     * @param goldReward    gold paid into the guild treasury on completion
     * @param minGuildLevel lowest guild level rank (1-5) eligible for this objective
     */
    record GuildQuestObjectiveDto(
        @Nullable String id,
        @Nullable String name,
        @Nullable String targetMobId,
        @Nullable String targetName,
        int requiredKills,
        int goldReward,
        int minGuildLevel
    ) {
    }
}
