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
 * @param phase          the current phase of the duel
 * @param opponent       the other player (challenger while pending, combatant while active)
 * @param ticksRemaining ticks left in the acceptance window while pending; zero while active
 */
public record PlayerDuelState(Phase phase, Username opponent, int ticksRemaining) {

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
    }

    /**
     * Creates a pending challenge state held against the challenged player.
     *
     * @param challenger   the player who issued the challenge
     * @param timeoutTicks the number of ticks the challenged player has to accept
     * @return a pending duel state
     */
    public static PlayerDuelState pending(Username challenger, int timeoutTicks) {
        return new PlayerDuelState(Phase.PENDING, challenger, timeoutTicks);
    }

    /**
     * Creates an active duel state held against one of the two engaged combatants.
     *
     * @param opponent the other combatant
     * @return an active duel state
     */
    public static PlayerDuelState active(Username opponent) {
        return new PlayerDuelState(Phase.ACTIVE, opponent, 0);
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
        return new PlayerDuelState(phase, opponent, ticksRemaining - 1);
    }

    /** Returns whether this pending challenge has run out its acceptance window. */
    public boolean expired() {
        return phase == Phase.PENDING && ticksRemaining <= 0;
    }
}
