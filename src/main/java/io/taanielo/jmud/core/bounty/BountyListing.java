package io.taanielo.jmud.core.bounty;

import java.util.Objects;

/**
 * A server-wide bounty summary for one mob type, as shown by {@code BOUNTY LIST}: the mob's display
 * name, the total reward pooled across every open backer, how many backers are stacked on it, how
 * long the oldest still-open stake has been posted, and how soon the next stake lapses.
 *
 * @param mobName         the mob type's display name
 * @param totalReward     the summed gold reward across all open backers of this mob type
 * @param backerCount     the number of distinct open backers
 * @param ageTicks        ticks elapsed since the oldest open stake on this mob type was posted
 * @param remainingTicks  ticks left before the soonest-to-lapse stake on this mob type expires
 */
public record BountyListing(
    String mobName, int totalReward, int backerCount, long ageTicks, long remainingTicks) {

    public BountyListing {
        Objects.requireNonNull(mobName, "mobName is required");
    }
}
