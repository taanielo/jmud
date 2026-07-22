package io.taanielo.jmud.core.bounty;

import java.util.Objects;

/**
 * A server-wide bounty summary for one target, as shown by {@code BOUNTY LIST}: the target's kind and
 * display name, the total reward pooled across every open backer, how many backers are stacked on it,
 * how long the oldest still-open stake has been posted, and how soon the next stake lapses.
 *
 * @param targetKind      whether the row aggregates a mob-type or a player target
 * @param targetName      the target's display name (mob type name or player username)
 * @param totalReward     the summed gold reward across all open backers of this target
 * @param backerCount     the number of distinct open backers
 * @param ageTicks        ticks elapsed since the oldest open stake on this target was posted
 * @param remainingTicks  ticks left before the soonest-to-lapse stake on this target expires
 */
public record BountyListing(
    BountyTargetKind targetKind,
    String targetName,
    int totalReward,
    int backerCount,
    long ageTicks,
    long remainingTicks) {

    public BountyListing {
        Objects.requireNonNull(targetKind, "targetKind is required");
        Objects.requireNonNull(targetName, "targetName is required");
    }
}
