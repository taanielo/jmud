package io.taanielo.jmud.core.guild;

/**
 * Rank of a member within a guild.
 *
 * <p>Only {@link #LEADER} and {@link #MEMBER} exist today; richer rank hierarchies (officers with
 * partial privileges, custom rank titles) are an explicit follow-up and out of scope here.
 */
public enum GuildRank {
    /** The single guild leader: may invite, kick, and disband. */
    LEADER,
    /** An ordinary guild member. */
    MEMBER
}
