package io.taanielo.jmud.core.bounty;

/**
 * Discriminates what a {@link Bounty} is placed on: a mob <em>type</em> (payable to whoever next kills
 * one of that type anywhere) or a specific rival <em>player</em> (payable to whoever defeats them in a
 * consensual {@code DUEL}). The two kinds share the same escrow, expiry, refund, cap and announcement
 * plumbing; only the target-resolution and payout trigger differ.
 */
public enum BountyTargetKind {

    /** A bounty on a mob type, claimed on the next kill of that type anywhere in the world. */
    MOB,

    /** A bounty on a specific player, claimed when they lose a consensual duel. */
    PLAYER
}
