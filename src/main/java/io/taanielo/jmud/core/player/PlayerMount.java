package io.taanielo.jmud.core.player;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Holds a player's transient "currently riding" state: which mount they are saddled up on and the
 * move-point discount that mount grants per step.
 *
 * <p>Like {@linkplain PlayerCombatState#stealthActive() stealth} and {@linkplain Player#isResting()
 * resting}, this is a stance-like flag that is <strong>in-memory only</strong> and never serialised:
 * players always relog dismounted, even though the mount item itself remains in their persisted
 * inventory. Being mounted is broken automatically when the rider enters combat or moves indoors,
 * mirroring how stealth is broken by a hostile action (AGENTS.md §5 — mutated only on the tick
 * thread).
 */
public final class PlayerMount {

    private static final PlayerMount DISMOUNTED = new PlayerMount(null, 0);

    /** Display name of the mount being ridden, or {@code null} when the player is on foot. */
    private final @Nullable String mountName;
    /** Move-point reduction applied to each step while mounted; zero while dismounted. */
    private final int moveDiscount;

    private PlayerMount(@Nullable String mountName, int moveDiscount) {
        this.mountName = mountName;
        this.moveDiscount = Math.max(0, moveDiscount);
    }

    /**
     * Returns the shared "on foot" instance.
     */
    public static PlayerMount dismounted() {
        return DISMOUNTED;
    }

    /**
     * Returns a mounted state for the given mount.
     *
     * @param mountName    the mount's display name; must not be blank
     * @param moveDiscount the per-step move-point reduction the mount grants; negative values are
     *                     clamped to zero
     */
    public static PlayerMount riding(String mountName, int moveDiscount) {
        Objects.requireNonNull(mountName, "Mount name is required");
        if (mountName.isBlank()) {
            throw new IllegalArgumentException("Mount name must not be blank");
        }
        return new PlayerMount(mountName, moveDiscount);
    }

    /**
     * Returns whether the player is currently riding a mount.
     */
    public boolean isMounted() {
        return mountName != null;
    }

    /**
     * Returns the display name of the mount being ridden, or {@code null} when on foot.
     */
    public @Nullable String mountName() {
        return mountName;
    }

    /**
     * Returns the per-step move-point reduction the current mount grants (zero while dismounted).
     */
    public int moveDiscount() {
        return isMounted() ? moveDiscount : 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlayerMount that)) {
            return false;
        }
        return moveDiscount == that.moveDiscount && Objects.equals(mountName, that.mountName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mountName, moveDiscount);
    }

    @Override
    public String toString() {
        return isMounted() ? "PlayerMount[" + mountName + ", -" + moveDiscount + " move]" : "PlayerMount[dismounted]";
    }
}
