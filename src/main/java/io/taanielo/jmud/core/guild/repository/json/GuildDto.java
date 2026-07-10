package io.taanielo.jmud.core.guild.repository.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a single guild file ({@code guilds/&lt;id&gt;.json}).
 */
record GuildDto(
    int schemaVersion,
    @Nullable String id,
    @Nullable String name,
    @Nullable String leaderId,
    @Nullable List<GuildMemberDto> members,
    int treasuryGold
) {

    /** JSON transfer object for one roster entry. */
    record GuildMemberDto(
        @Nullable String username,
        @Nullable String rank,
        int joinOrder
    ) {
    }
}
