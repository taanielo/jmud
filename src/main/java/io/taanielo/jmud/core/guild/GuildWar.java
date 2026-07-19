package io.taanielo.jmud.core.guild;

import java.util.Objects;

/**
 * Immutable value object describing a guild's side of an active guild war (issue #731).
 *
 * <p>A guild war is a running tally of consensual {@code DUEL} wins between the members of two rival
 * guilds. Both guilds involved in the same war hold their own {@code GuildWar} instance on their
 * {@link Guild} aggregate, each recorded from that guild's own perspective: {@link #ownPoints()} is
 * the holder guild's war-point score and {@link #opponentPoints()} is the {@link #opponent()} guild's
 * score. The two instances are kept in lock-step by {@link GuildWarService}, which updates both guilds
 * whenever a war point is scored, so their scores never diverge.
 *
 * <p>The first guild to reach {@link #POINTS_TO_WIN} war points wins the war automatically. War state
 * is persisted on the guild (additive, nullable) so a war survives a server restart; the transient
 * propose/accept handshake that precedes it is not persisted.
 */
public record GuildWar(GuildId opponent, int ownPoints, int opponentPoints) {

    /** War points a guild must reach to win the war outright. */
    public static final int POINTS_TO_WIN = 5;

    public GuildWar {
        Objects.requireNonNull(opponent, "GuildWar opponent is required");
        if (ownPoints < 0) {
            throw new IllegalArgumentException("GuildWar ownPoints must not be negative");
        }
        if (opponentPoints < 0) {
            throw new IllegalArgumentException("GuildWar opponentPoints must not be negative");
        }
    }

    /**
     * Starts a fresh, scoreless war against the given opponent guild.
     *
     * @param opponent the rival guild's id
     * @return a zero-zero {@link GuildWar}
     */
    public static GuildWar against(GuildId opponent) {
        return new GuildWar(opponent, 0, 0);
    }

    /**
     * Returns a copy of this war with the holder guild's own score incremented by one.
     *
     * @return the updated war
     */
    public GuildWar withOwnPoint() {
        return new GuildWar(opponent, ownPoints + 1, opponentPoints);
    }

    /**
     * Returns a copy of this war with the opponent guild's score incremented by one.
     *
     * @return the updated war
     */
    public GuildWar withOpponentPoint() {
        return new GuildWar(opponent, ownPoints, opponentPoints + 1);
    }

    /**
     * Returns {@code true} when the holder guild has reached the {@link #POINTS_TO_WIN} threshold and
     * has therefore won the war.
     *
     * @return whether the holder guild has won
     */
    public boolean isWonByHolder() {
        return ownPoints >= POINTS_TO_WIN;
    }
}
