package io.taanielo.jmud.core.world;

import org.jspecify.annotations.Nullable;

/**
 * Durability facet of an {@link Item}: the maximum durability of breakable gear and its current
 * value. A {@code null} {@link #max()} marks an unbreakable item. This is a construction-time value
 * object grouping the durability parameters so item features can grow without reshaping
 * {@link Item}'s constructor; the invariants ({@code max} positive, {@code 0 <= current <= max},
 * {@code current} null iff {@code max} null) are validated in {@link Item}.
 *
 * @param max     the maximum durability, or {@code null} for an unbreakable item
 * @param current the current durability, or {@code null} to default to {@code max} on a breakable
 *                item and required to stay {@code null} on an unbreakable one
 */
public record Durability(@Nullable Integer max, @Nullable Integer current) {

    private static final Durability NONE = new Durability(null, null);

    /**
     * Returns the shared "unbreakable" state (no max, no current).
     */
    public static Durability none() {
        return NONE;
    }

    /**
     * Returns breakable durability with the given max and current values.
     *
     * @param max     the maximum durability; must be positive
     * @param current the current durability, or {@code null} to default to {@code max}
     */
    public static Durability of(@Nullable Integer max, @Nullable Integer current) {
        return new Durability(max, current);
    }

    /**
     * Returns full breakable durability with the given max (current defaults to max).
     *
     * @param max the maximum durability; must be positive
     */
    public static Durability of(int max) {
        return new Durability(max, max);
    }

    /**
     * Returns whether this state describes breakable gear (i.e. has a max).
     */
    public boolean isBreakable() {
        return max != null;
    }
}
