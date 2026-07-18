package io.taanielo.jmud.core.bounty;

import java.util.Objects;

/**
 * A server-wide bounty summary for one mob type, as shown by {@code BOUNTY LIST}: the mob's display
 * name, the total reward pooled across every open backer, how many backers are stacked on it, and how
 * long the oldest still-open stake has been posted.
 *
 * @param mobName      the mob type's display name
 * @param totalReward  the summed gold reward across all open backers of this mob type
 * @param backerCount  the number of distinct open backers
 * @param ageTicks     ticks elapsed since the oldest open stake on this mob type was posted
 */
public record BountyListing(String mobName, int totalReward, int backerCount, long ageTicks) {

    public BountyListing {
        Objects.requireNonNull(mobName, "mobName is required");
    }
}
