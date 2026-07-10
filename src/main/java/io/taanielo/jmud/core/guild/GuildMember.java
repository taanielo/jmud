package io.taanielo.jmud.core.guild;

import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Immutable membership record of a single player within a guild.
 *
 * @param username  the member's username
 * @param rank      the member's rank
 * @param joinOrder a monotonically increasing sequence number assigned when the member joined;
 *                  lower means longer-tenured, so it is used to pick a successor leader on departure
 */
public record GuildMember(Username username, GuildRank rank, int joinOrder) {

    public GuildMember {
        Objects.requireNonNull(username, "Guild member username is required");
        Objects.requireNonNull(rank, "Guild member rank is required");
        if (joinOrder < 0) {
            throw new IllegalArgumentException("Guild member joinOrder must not be negative");
        }
    }

    /** Returns a copy of this member with the given rank. */
    public GuildMember withRank(GuildRank newRank) {
        return new GuildMember(username, newRank, joinOrder);
    }
}
