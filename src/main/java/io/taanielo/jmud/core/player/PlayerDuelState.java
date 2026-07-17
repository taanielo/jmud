package io.taanielo.jmud.core.player;

import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Transient, in-memory state of a consensual player-vs-player duel from the perspective of a single
 * player. Never persisted to the player save file.
 *
 * <p>A duel moves through two phases:
 * <ul>
 *   <li>{@link Phase#PENDING} — a challenge has been issued to this player but not yet accepted.
 *       {@link #opponent()} is the challenger and {@link #ticksRemaining()} counts down the
 *       acceptance window; when it reaches zero the request expires silently.</li>
 *   <li>{@link Phase#ACTIVE} — the duel has been accepted and both participants are engaged.
 *       {@link #opponent()} is the other combatant; {@link #ticksRemaining()} is unused (zero).</li>
 * </ul>
 *
 * <p>All transitions are driven by {@link DuelService} on the tick thread (AGENTS.md §5).
 *
 * <p>A duel may optionally carry a {@code wager} of gold (issue #661): a positive amount staked by
 * both participants, transferred loser-to-winner only when the duel actually resolves. Ordinary
 * duels default to an unwagered stake of {@code 0}. The wager is transient like the rest of this
 * state and is never persisted.
 *
 * @param phase          the current phase of the duel
 * @param opponent       the other player (challenger while pending, combatant while active)
 * @param ticksRemaining ticks left in the acceptance window while pending; zero while active
 * @param wager          the gold staked on the duel, or {@code 0} when unwagered
 */
public record PlayerDuelState(Phase phase, Username opponent, int ticksRemaining, long wager) {

    /** Lifecycle phase of a duel relationship. */
    public enum Phase {
        /** A challenge has been issued and is awaiting acceptance. */
        PENDING,
        /** The duel has been accepted and both players are engaged. */
        ACTIVE
    }

    public PlayerDuelState {
        Objects.requireNonNull(phase, "Duel phase is required");
        Objects.requireNonNull(opponent, "Opponent is required");
        if (wager < 0) {
            throw new IllegalArgumentException("Duel wager cannot be negative");
        }
    }

    /**
     * Creates an unwagered pending challenge state held against the challenged player.
     *
     * @param challenger   the player who issued the challenge
     * @param timeoutTicks the number of ticks the challenged player has to accept
     * @return a pending, unwagered duel state
     */
    public static PlayerDuelState pending(Username challenger, int timeoutTicks) {
        return pending(challenger, timeoutTicks, 0L);
    }

    /**
     * Creates a pending challenge state held against the challenged player, optionally staked with a
     * gold wager.
     *
     * @param challenger   the player who issued the challenge
     * @param timeoutTicks the number of ticks the challenged player has to accept
     * @param wager        the gold staked on the duel, or {@code 0} when unwagered
     * @return a pending duel state
     */
    public static PlayerDuelState pending(Username challenger, int timeoutTicks, long wager) {
        return new PlayerDuelState(Phase.PENDING, challenger, timeoutTicks, wager);
    }

    /**
     * Creates an unwagered active duel state held against one of the two engaged combatants.
     *
     * @param opponent the other combatant
     * @return an active, unwagered duel state
     */
    public static PlayerDuelState active(Username opponent) {
        return active(opponent, 0L);
    }

    /**
     * Creates an active duel state held against one of the two engaged combatants, optionally staked
     * with a gold wager.
     *
     * @param opponent the other combatant
     * @param wager    the gold staked on the duel, or {@code 0} when unwagered
     * @return an active duel state
     */
    public static PlayerDuelState active(Username opponent, long wager) {
        return new PlayerDuelState(Phase.ACTIVE, opponent, 0, wager);
    }

    /** Returns whether this duel carries a positive gold wager. */
    public boolean isWagered() {
        return wager > 0;
    }

    /** Returns whether this state is a pending, not-yet-accepted challenge. */
    public boolean isPending() {
        return phase == Phase.PENDING;
    }

    /** Returns whether this state is an accepted, active duel. */
    public boolean isActive() {
        return phase == Phase.ACTIVE;
    }

    /**
     * Returns a copy of this pending state with one tick removed from the acceptance window;
     * active states are returned unchanged.
     */
    public PlayerDuelState tickDown() {
        if (phase != Phase.PENDING) {
            return this;
        }
        return new PlayerDuelState(phase, opponent, ticksRemaining - 1, wager);
    }

    /** Returns whether this pending challenge has run out its acceptance window. */
    public boolean expired() {
        return phase == Phase.PENDING && ticksRemaining <= 0;
    }
}
