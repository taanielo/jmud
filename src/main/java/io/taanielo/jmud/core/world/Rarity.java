package io.taanielo.jmud.core.world;

import java.util.Locale;
import java.util.Objects;

/**
 * Rarity tier of an {@link Item}, governing its colored display name and which stat affixes it may
 * carry. Tiers are ordered from most common to rarest.
 *
 * <p>Rarity is game content persisted as a lowercase string on item JSON (see the {@code rarity}
 * field in the item schema); an absent value maps to {@link #COMMON}, keeping legacy item files
 * fully backward compatible.
 */
public enum Rarity {

    /** The default tier: no visual highlight and no affixes. */
    COMMON("common", 0),
    /** An uplifted tier that may roll a single stat affix. */
    UNCOMMON("uncommon", 1),
    /** The top tier that rolls one or two stat affixes. */
    RARE("rare", 2);

    private final String id;
    private final int tier;

    Rarity(String id, int tier) {
        this.id = id;
        this.tier = tier;
    }

    /**
     * Returns the lowercase persisted identifier for this tier (e.g. {@code "uncommon"}).
     *
     * @return the JSON identifier for this rarity tier
     */
    public String id() {
        return id;
    }

    /**
     * Resolves a rarity tier from its persisted identifier, treating {@code null} or blank as
     * {@link #COMMON} so items without an explicit {@code rarity} field load unchanged.
     *
     * @param id the persisted identifier, or {@code null}/blank for the default tier
     * @return the matching rarity tier
     * @throws IllegalArgumentException if {@code id} is non-blank but does not name a known tier
     */
    public static Rarity fromId(String id) {
        if (id == null || id.isBlank()) {
            return COMMON;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (Rarity rarity : values()) {
            if (rarity.id.equals(normalized)) {
                return rarity;
            }
        }
        throw new IllegalArgumentException("Unknown rarity tier: " + id);
    }

    /**
     * Returns whether this tier is the default {@link #COMMON} tier, which carries no visual
     * highlight and no affixes.
     *
     * @return {@code true} when this is {@link #COMMON}
     */
    public boolean isCommon() {
        return this == COMMON;
    }

    /**
     * Returns whether this tier is at least as rare as the given tier. The comparison uses an
     * explicit stable rank rather than enum declaration order (via {@code ordinal()}), so
     * inserting or reordering tiers later cannot silently shift rarity-quality thresholds such as
     * guaranteed world-boss loot.
     *
     * @param other the tier to compare against
     * @return {@code true} when this tier's rank is greater than or equal to {@code other}'s
     */
    public boolean isAtLeast(Rarity other) {
        return this.tier >= other.tier;
    }

    /**
     * Returns the identifier of the given rarity, or {@link #COMMON}'s identifier when
     * {@code rarity} is {@code null}.
     *
     * @param rarity the rarity to describe, may be {@code null}
     * @return the persisted identifier, never {@code null}
     */
    public static String idOrDefault(Rarity rarity) {
        return Objects.requireNonNullElse(rarity, COMMON).id();
    }
}
