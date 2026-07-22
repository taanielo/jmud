package io.taanielo.jmud.core.bounty;

import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Immutable value object describing a single open bounty: one {@link #backer() backer}'s gold
 * {@link #reward() stake} placed on a {@link #targetKind() target}, either a mob <em>type</em>
 * (payable to whoever next kills a mob of that type) or a rival <em>player</em> (payable to whoever
 * defeats them in a consensual duel), identified by its {@link #targetId() id}.
 *
 * <p>Several backers may hold bounties on the same {@link #targetId()} simultaneously; each is a
 * separate {@code Bounty} and the target's advertised reward is the sum of every open backer's stake.
 * At most one open bounty exists per {@code (backer, targetKind, targetId)} triple.
 *
 * @param backer      the player who posted and funds this bounty
 * @param targetKind  whether the bounty targets a mob type or a specific player
 * @param targetId    the target's stable identity: a mob template id ({@link BountyTargetKind#MOB}) or
 *                    the target player's username value ({@link BountyTargetKind#PLAYER})
 * @param targetName  the target's display name, captured at post time for listings/announcements
 * @param reward      the escrowed gold stake; always positive
 * @param postedTick  the game tick at which the bounty was posted (used to report its age)
 */
public record Bounty(
    Username backer,
    BountyTargetKind targetKind,
    String targetId,
    String targetName,
    int reward,
    long postedTick
) {
    public Bounty {
        Objects.requireNonNull(backer, "backer is required");
        Objects.requireNonNull(targetKind, "targetKind is required");
        Objects.requireNonNull(targetId, "targetId is required");
        Objects.requireNonNull(targetName, "targetName is required");
        if (targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        if (reward <= 0) {
            throw new IllegalArgumentException("reward must be positive");
        }
    }

    /**
     * Creates a mob-type bounty.
     *
     * @param backer        the funding player
     * @param mobTemplateId the mob type's template id
     * @param mobName       the mob type's display name
     * @param reward        the escrowed gold stake; must be positive
     * @param postedTick    the tick the bounty was posted
     * @return the mob-target bounty
     */
    public static Bounty onMob(
        Username backer, String mobTemplateId, String mobName, int reward, long postedTick) {
        return new Bounty(backer, BountyTargetKind.MOB, mobTemplateId, mobName, reward, postedTick);
    }

    /**
     * Creates a player-target bounty. The target's username value is used as both the stable id and the
     * display name.
     *
     * @param backer     the funding player
     * @param target     the player the bounty is placed on
     * @param reward     the escrowed gold stake; must be positive
     * @param postedTick the tick the bounty was posted
     * @return the player-target bounty
     */
    public static Bounty onPlayer(Username backer, Username target, int reward, long postedTick) {
        Objects.requireNonNull(target, "target is required");
        return new Bounty(backer, BountyTargetKind.PLAYER, target.getValue(), target.getValue(), reward, postedTick);
    }

    /**
     * Returns whether this bounty targets a mob type.
     *
     * @return {@code true} when {@link #targetKind()} is {@link BountyTargetKind#MOB}
     */
    public boolean targetsMob() {
        return targetKind == BountyTargetKind.MOB;
    }

    /**
     * Returns whether this bounty targets a specific player.
     *
     * @return {@code true} when {@link #targetKind()} is {@link BountyTargetKind#PLAYER}
     */
    public boolean targetsPlayer() {
        return targetKind == BountyTargetKind.PLAYER;
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
