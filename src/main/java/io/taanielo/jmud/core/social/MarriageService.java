package io.taanielo.jmud.core.social;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.tick.Tickable;

/**
 * In-memory registry of pending, consensual marriage proposals (see the MARRY command).
 *
 * <p>Only the <em>proposal</em> half of marriage is transient and owned here; the persistent bond
 * itself is stored on {@link io.taanielo.jmud.core.player.Player} so it survives logout and server
 * restart. This service mirrors {@link io.taanielo.jmud.core.player.DuelService}: proposals are keyed
 * by the target (proposed-to) {@link Username}, each carries a countdown until it silently expires,
 * and mutating operations are {@code synchronized} so proposal bookkeeping stays consistent when a
 * reader thread clears state on disconnect concurrently with the tick thread. All game-state mutation
 * itself still happens on the tick thread (AGENTS.md §5).
 *
 * <p>As a {@link Tickable}, this service decrements each pending proposal's window once per tick and
 * silently discards proposals that time out.
 */
public class MarriageService implements Tickable {

    /** Default acceptance window for a marriage proposal, in ticks (~60 seconds at 1s/tick). */
    public static final int DEFAULT_TIMEOUT_TICKS = 60;

    /** target player → the pending proposal awaiting their response. */
    private final Map<Username, MarriageProposal> proposals = new ConcurrentHashMap<>();

    /**
     * Records a pending marriage proposal from {@code proposer} to {@code target}, starting the
     * default acceptance window. Any existing proposal held against {@code target} is replaced.
     *
     * @param proposer the player proposing marriage
     * @param target   the proposed-to player
     */
    public synchronized void propose(Username proposer, Username target) {
        Objects.requireNonNull(proposer, "Proposer is required");
        Objects.requireNonNull(target, "Target is required");
        proposals.put(target, new MarriageProposal(proposer, DEFAULT_TIMEOUT_TICKS));
    }

    /**
     * Returns the proposer of a pending proposal awaiting {@code target}'s response, if any.
     *
     * @param target the proposed-to player
     * @return the proposer's username, or empty when no pending proposal exists
     */
    public synchronized Optional<Username> pendingProposer(Username target) {
        Objects.requireNonNull(target, "Target is required");
        MarriageProposal proposal = proposals.get(target);
        return proposal == null ? Optional.empty() : Optional.of(proposal.proposer());
    }

    /**
     * Returns whether the given player currently has an outstanding proposal awaiting their response.
     *
     * @param target the proposed-to player
     * @return {@code true} when a pending proposal is held against {@code target}
     */
    public synchronized boolean hasPendingProposal(Username target) {
        Objects.requireNonNull(target, "Target is required");
        return proposals.containsKey(target);
    }

    /**
     * Consumes and returns the pending proposal awaiting {@code target}'s response, removing it from
     * the registry. Used by both accept and decline, since either resolves the proposal.
     *
     * @param target the proposed-to player
     * @return the proposer's username, or empty when there was nothing pending
     */
    public synchronized Optional<Username> resolve(Username target) {
        Objects.requireNonNull(target, "Target is required");
        MarriageProposal proposal = proposals.remove(target);
        return proposal == null ? Optional.empty() : Optional.of(proposal.proposer());
    }

    /**
     * Clears every proposal touching the given player: one awaiting their response and any they issued
     * to someone else. Called when a player disconnects or leaves the room, so a stale proposal can
     * never block the other party.
     *
     * @param player the player whose proposal involvement should be cleared
     */
    public synchronized void clearFor(Username player) {
        Objects.requireNonNull(player, "Player is required");
        proposals.remove(player);
        proposals.values().removeIf(proposal -> proposal.proposer().equals(player));
    }

    @Override
    public synchronized void tick() {
        proposals.replaceAll((target, proposal) -> proposal.tickDown());
        proposals.values().removeIf(MarriageProposal::expired);
    }
}
