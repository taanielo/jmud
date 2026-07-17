package io.taanielo.jmud.core.player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.tick.Tickable;

/**
 * In-memory registry of consensual player-vs-player duels.
 *
 * <p>Tracks two kinds of relationship, both keyed by player {@link Username}:
 * <ul>
 *   <li>a <em>pending</em> challenge, held against the challenged player until they accept or the
 *       acceptance window expires;</li>
 *   <li>an <em>active</em> duel, held against both engaged combatants.</li>
 * </ul>
 *
 * <p>State is transient and never persisted: duels vanish on server restart. Mutating operations
 * are {@code synchronized} so that duel bookkeeping stays consistent even when a reader thread
 * clears state on disconnect concurrently with the tick thread; all game-state mutation itself
 * still happens on the tick thread (AGENTS.md §5).
 *
 * <p>As a {@link Tickable}, this service decrements each pending challenge's acceptance window once
 * per tick and silently discards challenges that time out.
 */
public class DuelService implements Tickable {

    /** Default acceptance window for a duel challenge, in ticks (~30 seconds at 1s/tick). */
    public static final int DEFAULT_TIMEOUT_TICKS = 30;

    /** player → their current duel relationship (pending challenge or active duel). */
    private final Map<Username, PlayerDuelState> states = new ConcurrentHashMap<>();

    /**
     * Records a pending duel challenge from {@code challenger} to {@code target}, starting the
     * default acceptance window. Any existing challenge held against {@code target} is replaced.
     *
     * @param challenger the player issuing the challenge
     * @param target     the challenged player
     */
    public synchronized void requestDuel(Username challenger, Username target) {
        requestDuel(challenger, target, 0L);
    }

    /**
     * Records a pending duel challenge from {@code challenger} to {@code target} staked with an
     * optional gold wager, starting the default acceptance window. Any existing challenge held
     * against {@code target} is replaced.
     *
     * <p>The wager is not escrowed here; it is only transferred loser-to-winner when the duel
     * actually resolves (issue #661). A wager of {@code 0} marks an ordinary risk-free duel.
     *
     * @param challenger the player issuing the challenge
     * @param target     the challenged player
     * @param wager      the gold staked on the duel, or {@code 0} when unwagered
     */
    public synchronized void requestDuel(Username challenger, Username target, long wager) {
        Objects.requireNonNull(challenger, "Challenger is required");
        Objects.requireNonNull(target, "Target is required");
        states.put(target, PlayerDuelState.pending(challenger, DEFAULT_TIMEOUT_TICKS, wager));
    }

    /**
     * Returns the challenger of a pending duel awaiting {@code target}'s acceptance, if any.
     *
     * @param target the challenged player
     * @return the challenger's username, or empty when no pending challenge exists
     */
    public synchronized Optional<Username> pendingChallenger(Username target) {
        Objects.requireNonNull(target, "Target is required");
        PlayerDuelState state = states.get(target);
        return state != null && state.isPending() ? Optional.of(state.opponent()) : Optional.empty();
    }

    /**
     * Promotes a pending challenge into an active duel, engaging both participants.
     *
     * @param challenger the player who issued the challenge
     * @param target     the player who accepted it
     */
    public synchronized void activate(Username challenger, Username target) {
        Objects.requireNonNull(challenger, "Challenger is required");
        Objects.requireNonNull(target, "Target is required");
        PlayerDuelState pending = states.get(target);
        long wager = pending != null && pending.isPending() ? pending.wager() : 0L;
        states.put(challenger, PlayerDuelState.active(target, wager));
        states.put(target, PlayerDuelState.active(challenger, wager));
    }

    /**
     * Returns whether the given player is currently engaged in an active duel.
     *
     * @param player the player to check
     * @return {@code true} when the player is in an active duel
     */
    public boolean isDueling(Username player) {
        Objects.requireNonNull(player, "Player is required");
        PlayerDuelState state = states.get(player);
        return state != null && state.isActive();
    }

    /**
     * Returns whether {@code a} and {@code b} are engaged in an active duel with each other.
     *
     * @param a one participant
     * @param b the other participant
     * @return {@code true} when both are dueling each other
     */
    public boolean areDueling(Username a, Username b) {
        Objects.requireNonNull(a, "First player is required");
        Objects.requireNonNull(b, "Second player is required");
        PlayerDuelState state = states.get(a);
        return state != null && state.isActive() && state.opponent().equals(b);
    }

    /**
     * Returns the active-duel opponent of the given player, if any.
     *
     * @param player the player to look up
     * @return the opponent's username, or empty when not in an active duel
     */
    public Optional<Username> opponentOf(Username player) {
        Objects.requireNonNull(player, "Player is required");
        PlayerDuelState state = states.get(player);
        return state != null && state.isActive() ? Optional.of(state.opponent()) : Optional.empty();
    }

    /**
     * Returns the raw duel state for the given player, primarily for tests and diagnostics.
     *
     * @param player the player to look up
     * @return the player's duel state, or empty when they have none
     */
    public Optional<PlayerDuelState> stateOf(Username player) {
        Objects.requireNonNull(player, "Player is required");
        return Optional.ofNullable(states.get(player));
    }

    /**
     * Returns the gold wager currently staked on the given player's duel relationship, whether the
     * duel is pending or active.
     *
     * @param player the player to look up
     * @return the staked wager, or {@code 0} when the player has no duel state or the duel is
     *         unwagered
     */
    public long wagerOf(Username player) {
        Objects.requireNonNull(player, "Player is required");
        PlayerDuelState state = states.get(player);
        return state == null ? 0L : state.wager();
    }

    /**
     * Ends an active duel between the two named players, disengaging both.
     *
     * @param a one participant
     * @param b the other participant
     */
    public synchronized void endDuel(Username a, Username b) {
        Objects.requireNonNull(a, "First player is required");
        Objects.requireNonNull(b, "Second player is required");
        states.remove(a);
        states.remove(b);
    }

    /**
     * Clears every duel relationship touching the given player: their own pending or active state,
     * the opponent's matching state, and any pending challenge this player issued to someone else.
     *
     * <p>Called when a player changes rooms or disconnects, so a stale duel can never block the
     * other participant.
     *
     * @param player the player whose duel involvement should be cleared
     */
    public synchronized void clearFor(Username player) {
        Objects.requireNonNull(player, "Player is required");
        PlayerDuelState state = states.remove(player);
        if (state != null && state.isActive()) {
            states.remove(state.opponent());
        }
        states.values().removeIf(other -> other.isPending() && other.opponent().equals(player));
    }

    @Override
    public synchronized void tick() {
        states.replaceAll((player, state) -> state.tickDown());
        states.values().removeIf(PlayerDuelState::expired);
    }
}
