package io.taanielo.jmud.core.bounty;

import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Immutable value object describing a single open bounty: one {@link #backer() backer}'s gold
 * {@link #reward() stake} placed on a mob <em>type</em> (identified by its template
 * {@link #mobTemplateId() id}), payable to whoever next kills a mob of that type.
 *
 * <p>Several backers may hold bounties on the same {@link #mobTemplateId()} simultaneously; each is a
 * separate {@code Bounty} and the type's advertised reward is the sum of every open backer's stake.
 * At most one open bounty exists per {@code (backer, mobTemplateId)} pair.
 *
 * @param backer         the player who posted and funds this bounty
 * @param mobTemplateId  the template id of the mob type the bounty targets
 * @param mobName        the mob type's display name, captured at post time for listings/announcements
 * @param reward         the escrowed gold stake; always positive
 * @param postedTick     the game tick at which the bounty was posted (used to report its age)
 */
public record Bounty(
    Username backer,
    String mobTemplateId,
    String mobName,
    int reward,
    long postedTick
) {
    public Bounty {
        Objects.requireNonNull(backer, "backer is required");
        Objects.requireNonNull(mobTemplateId, "mobTemplateId is required");
        Objects.requireNonNull(mobName, "mobName is required");
        if (mobTemplateId.isBlank()) {
            throw new IllegalArgumentException("mobTemplateId must not be blank");
        }
        if (reward <= 0) {
            throw new IllegalArgumentException("reward must be positive");
        }
    }

    /**
     * Returns whether this bounty has gone unclaimed long enough to expire, as a pure function of its
     * {@link #postedTick()} and the configured lifespan — no stored expiry field is needed.
     *
     * @param currentTick the current game tick
     * @param expiryTicks the number of ticks a bounty stays open before expiring; must be positive
     * @return {@code true} when at least {@code expiryTicks} ticks have elapsed since it was posted
     */
    public boolean isExpired(long currentTick, long expiryTicks) {
        return currentTick - postedTick >= expiryTicks;
    }

    /**
     * Returns the ticks remaining before this bounty expires, clamped at zero.
     *
     * @param currentTick the current game tick
     * @param expiryTicks the number of ticks a bounty stays open before expiring
     * @return the ticks left before expiry, never negative
     */
    public long ticksRemaining(long currentTick, long expiryTicks) {
        return Math.max(0, postedTick + expiryTicks - currentTick);
    }
}
