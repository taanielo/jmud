package io.taanielo.jmud.core.guild;

/**
 * Rank of a member within a guild.
 *
 * <p>Three ranks exist: {@link #LEADER} (full control), {@link #OFFICER} (delegated recruiting and
 * moderation), and {@link #MEMBER} (ordinary member). Officers may invite and kick but cannot
 * promote, demote, withdraw from the treasury, or disband the guild — those remain leader-only.
 */
public enum GuildRank {
    /** The single guild leader: may invite, kick, promote, demote, withdraw, and disband. */
    LEADER("Leader"),
    /** A trusted officer with delegated recruiting/moderation: may invite and kick. */
    OFFICER("Officer"),
    /** An ordinary guild member. */
    MEMBER("Member");

    private final String displayName;

    GuildRank(String displayName) {
        this.displayName = displayName;
    }

    /** Returns the human-readable, title-cased name of this rank (e.g. {@code "Officer"}). */
    public String displayName() {
        return displayName;
    }
}
