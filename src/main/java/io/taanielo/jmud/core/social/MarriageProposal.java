package io.taanielo.jmud.core.social;

import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;

/**
 * A pending marriage proposal awaiting the target player's response, held transiently by
 * {@link MarriageService}.
 *
 * <p>The proposal is keyed by the target (the proposed-to player) inside the service and remembers
 * who issued it together with a countdown, in ticks, until it silently expires. Instances are
 * immutable value objects; {@link #tickDown()} returns a fresh copy with the window reduced by one.
 *
 * @param proposer        the player who issued the proposal
 * @param ticksRemaining  ticks left in the acceptance window before the proposal lapses
 */
public record MarriageProposal(Username proposer, int ticksRemaining) {

    /**
     * Creates a proposal, validating the proposer and clamping the window to at least zero.
     */
    public MarriageProposal {
        Objects.requireNonNull(proposer, "Proposer is required");
        ticksRemaining = Math.max(0, ticksRemaining);
    }

    /**
     * Returns a copy of this proposal with its acceptance window reduced by one tick.
     *
     * @return the aged proposal
     */
    public MarriageProposal tickDown() {
        return new MarriageProposal(proposer, Math.max(0, ticksRemaining - 1));
    }

    /**
     * Returns {@code true} when the acceptance window has fully elapsed.
     *
     * @return {@code true} when this proposal has timed out
     */
    public boolean expired() {
        return ticksRemaining <= 0;
    }
}
